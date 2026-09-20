package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.entity.ChatMessageEntity;
import dev.saberlabs.coffeechat.entity.ChatSessionEntity;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import dev.saberlabs.coffeechat.repository.ChatMessageRepository;
import dev.saberlabs.coffeechat.repository.ChatSessionRepository;
import dev.saberlabs.coffeechat.repository.OrderRepository;
import dev.saberlabs.coffeechat.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The only writer of {@code chat_sessions} (and of {@code chat_messages}). Every method is one short,
 * self-contained transaction, run through a {@link TransactionTemplate} rather than {@code @Transactional}
 * so a caller (the non-transactional matchmaker) can never accidentally widen it, and so a failed
 * insert can be followed by a fresh read in a new transaction.
 *
 * <p>Status changes are conditional {@code UPDATE}s ({@code activateIfWaiting}, {@code endIfNotInactive}),
 * so two racing operations on one session can never both win; the loser gets 0 and reacts.
 * It holds no matching state: that is {@link BaristaQueue}'s, and never written here.
 */
@Service
public class ChatSessionStore {

    static final String ACTIVE_CUSTOMER_CONSTRAINT = "uq_chat_sessions_active_customer";
    static final String ACTIVE_BARISTA_CONSTRAINT = "uq_chat_sessions_active_barista";
    static final String SYSTEM_SENDER = "System";

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final OrderRepository orders;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public ChatSessionStore(@NotNull ChatSessionRepository sessions,
                            @NotNull ChatMessageRepository messages,
                            @NotNull UserRepository users,
                            @NotNull OrderRepository orders,
                            @NotNull ApplicationEventPublisher events,
                            @NotNull PlatformTransactionManager transactionManager) {
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.messages = Objects.requireNonNull(messages, "messages cannot be null");
        this.users = Objects.requireNonNull(users, "users cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        Objects.requireNonNull(transactionManager, "transactionManager cannot be null");
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /**
     * Opens a WAITING session for the customer.
     *
     * @throws CustomerNotFoundException        if there is no such user
     * @throws ChatSessionAlreadyOpenException  if the customer already has a non-INACTIVE session; decided by
     *                                          the database's partial unique index, so it holds under a race
     */
    public SessionView createWaiting(long customerId) {
        try {
            return tx.execute(status -> {
                UserEntity customer = users.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
                return view(sessions.saveAndFlush(new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now())));
            });
        } catch (DataIntegrityViolationException e) {
            if (!mentions(e, ACTIVE_CUSTOMER_CONSTRAINT)) {
                throw e;
            }
            Long existing = readTx.execute(status -> sessions.findByCustomerIdAndStatusNot(customerId, SessionStatus.INACTIVE)
                    .map(ChatSessionEntity::id).orElse(null));
            throw new ChatSessionAlreadyOpenException(customerId, existing);
        }
    }

    /**
     * Turns a WAITING session into an ACTIVE one with this barista, writes the "joined" system message and
     * publishes {@link ChatMatchedEvent}, all in one transaction.
     *
     * @return false, changing nothing, if the session is no longer WAITING (it ended, or was matched)
     * @throws ChatBaristaUnavailableException if the barista cannot serve (no such user, not a BARISTA, or
     *                                         already ACTIVE elsewhere: {@code uq_chat_sessions_active_barista})
     */
    public boolean activate(long sessionId, long baristaId) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                UserEntity barista = users.findById(baristaId)
                        .orElseThrow(() -> new ChatBaristaUnavailableException(baristaId, "no such user"));
                if (barista.role() != Role.BARISTA) {
                    throw new ChatBaristaUnavailableException(baristaId, "user is a " + barista.role());
                }
                if (sessions.activateIfWaiting(sessionId, barista) == 0) {
                    return false;
                }
                ChatSessionEntity session = sessions.findById(sessionId).orElseThrow();
                insert(session, MessageType.SYSTEM_MESSAGE, null, SYSTEM_SENDER, barista.name() + " joined the chat", null);
                events.publishEvent(new ChatMatchedEvent(sessionId, session.customer().id(), baristaId));
                return true;
            }));
        } catch (DataIntegrityViolationException e) {
            if (mentions(e, ACTIVE_BARISTA_CONSTRAINT)) {
                throw new ChatBaristaUnavailableException(baristaId, "already has an active session");
            }
            throw e;
        }
    }

    /**
     * Ends a session and writes the "ended" system message.
     *
     * @return true if this call ended it; false if it was already INACTIVE or does not exist (idempotent)
     */
    public boolean end(long sessionId) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (sessions.endIfNotInactive(sessionId) == 0) {
                return false;
            }
            insert(sessions.findById(sessionId).orElseThrow(), MessageType.SYSTEM_MESSAGE, null, SYSTEM_SENDER, "The chat has ended", null);
            return true;
        }));
    }

    /**
     * Appends a message. Content is stored as given: validation (trim, blank, length) is the caller's, with
     * the database CHECKs as the backstop.
     */
    public MessageView addMessage(long sessionId, @NotNull MessageType type, @Nullable Long senderId, @NotNull String senderName,
                                  @NotNull String content, @Nullable Long orderId) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(senderName, "senderName cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        return tx.execute(status -> {
            ChatSessionEntity session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("No session " + sessionId));
            UserEntity sender = senderId == null ? null : users.getReferenceById(senderId);
            OrderEntity order = orderId == null ? null : orders.getReferenceById(orderId);
            return insert(session, type, sender, senderName, content, order);
        });
    }

    public Optional<SessionView> find(long sessionId) {
        return Optional.ofNullable(readTx.execute(status -> sessions.findById(sessionId).map(ChatSessionStore::view).orElse(null)));
    }

    /** Sessions in a status, oldest first. */
    public List<SessionView> findByStatus(@NotNull SessionStatus sessionStatus) {
        Objects.requireNonNull(sessionStatus, "sessionStatus cannot be null");
        return readTx.execute(status -> sessions.findByStatusOrderByIdAsc(sessionStatus).stream().map(ChatSessionStore::view).toList());
    }

    /** The session's messages, oldest first (sent_at, then id). */
    public List<MessageView> history(long sessionId) {
        return readTx.execute(status -> messages.findBySessionIdOrderBySentAtAscIdAsc(sessionId).stream()
                .map(ChatSessionStore::view).toList());
    }

    private MessageView insert(ChatSessionEntity session, MessageType type, @Nullable UserEntity sender, String senderName,
                               String content, @Nullable OrderEntity order) {
        return view(messages.saveAndFlush(new ChatMessageEntity(session, type, sender, senderName, content, Instant.now(), order)));
    }

    private static SessionView view(ChatSessionEntity s) {
        return new SessionView(s.id(), s.customer().id(), s.barista() == null ? null : s.barista().id(), s.status(), s.createdAt());
    }

    private static MessageView view(ChatMessageEntity m) {
        return new MessageView(m.id(), m.session().id(), m.type(), m.sender() == null ? null : m.sender().id(),
                m.senderName(), m.content(), m.sentAt(), m.order() == null ? null : m.order().id());
    }

    private static boolean mentions(Throwable e, String text) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
