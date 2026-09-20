package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.OrderCommandParser.MissingCoffeeType;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.NotAnOrder;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.Parsed;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.UnknownCoffeeType;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.UnknownExtras;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeNotOnMenuException;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.ShopClosedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.StaffAccess;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The chat use cases, with their authorization rules. Deliberately NOT {@code @Transactional}: it
 * orchestrates several short, independent transactions (each in {@link ChatSessionStore}, or inside
 * {@link CoffeeShopFacade}), because the {@code /order} path must not let a failure in one step undo another.
 *
 * <p><b>Who may do what.</b> Every operation needs a real user ({@link Actor#SYSTEM} is rejected: nothing
 * here is automation). Only a CUSTOMER starts a chat; only a BARISTA registers as ready/offline; a session's
 * customer, its barista, or any MANAGER may end it or read its history; only its two participants may post,
 * and only while it is ACTIVE.
 *
 * <p><b>The {@code /order} path (Flow C).</b> A CUSTOMER's message that starts with {@code /order} is three
 * separate transactions, so each outcome is durable independently:
 * <ol>
 *   <li><b>T1</b> the customer's message is stored (it is always kept, order or not);</li>
 *   <li><b>T2</b> {@code CoffeeShopFacade.placeOrder} (never a repository or service directly: the facade is the
 *       only door into the order lifecycle);</li>
 *   <li><b>T3</b> a SYSTEM message with the outcome, linked to the order, retried up to
 *       {@value #CONFIRMATION_ATTEMPTS} times; if it still fails the order stands, an ERROR is logged, and the
 *       result still carries the order id.</li>
 * </ol>
 * A refused order (shop closed, not on the menu, malformed command) is not an error to the caller: the message
 * was accepted and the reply says why nothing was ordered.
 *
 * <p><b>Known limitation: no idempotency key.</b> Retrying an identical {@code /order} message (a client
 * timeout, a double tap) is a new message and places a SECOND order. What is guaranteed is that one accepted
 * message yields exactly one stored CHAT_MESSAGE and at most one order, even when T3 is retried.
 *
 * <p>The ACTIVE/participant checks here are a fast, friendly first pass; the authoritative check is repeated
 * inside the insert's own transaction under a row lock ({@code ChatSessionStore.addMessage}), so a session that
 * ends between the two cannot receive a message.
 */
@Service
public class ChatService {

    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int CONFIRMATION_ATTEMPTS = 3;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final Set<Role> CUSTOMERS = EnumSet.of(Role.CUSTOMER);
    private static final Set<Role> BARISTAS = EnumSet.of(Role.BARISTA);
    private static final Set<Role> ANYONE = EnumSet.allOf(Role.class);

    /**
     * What sending a message produced.
     *
     * @param message the stored message
     * @param reply   the system reply to an {@code /order} command (success or explanation), else null
     * @param orderId the order placed by this message, else null. Present even if the confirmation could not be stored
     */
    public record SendResult(MessageView message, @Nullable MessageView reply, @Nullable Long orderId) {
    }

    private final ChatMatchmaker matchmaker;
    private final ChatSessionStore store;
    private final OrderCommandParser parser;
    private final CoffeeShopFacade facade;
    private final StaffAccess staffAccess;
    private final UserRepository users;

    public ChatService(@NotNull ChatMatchmaker matchmaker,
                       @NotNull ChatSessionStore store,
                       @NotNull OrderCommandParser parser,
                       @NotNull CoffeeShopFacade facade,
                       @NotNull StaffAccess staffAccess,
                       @NotNull UserRepository users) {
        this.matchmaker = Objects.requireNonNull(matchmaker, "matchmaker cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.parser = Objects.requireNonNull(parser, "parser cannot be null");
        this.facade = Objects.requireNonNull(facade, "facade cannot be null");
        this.staffAccess = Objects.requireNonNull(staffAccess, "staffAccess cannot be null");
        this.users = Objects.requireNonNull(users, "users cannot be null");
    }

    /**
     * A customer asks to chat: a WAITING session, matched at once if a barista is ready.
     *
     * @throws ChatSessionAlreadyOpenException if they already have an open session (409, with its id)
     */
    public SessionView startChat(@NotNull Actor actor) {
        requireNoTransaction();
        UserEntity customer = requireUser(actor, CUSTOMERS, "start a chat");
        return matchmaker.open(customer.id());
    }

    /** A barista becomes available for the longest-waiting customer. */
    public void baristaReady(@NotNull Actor actor) {
        requireNoTransaction();
        matchmaker.baristaReady(requireBarista(actor));
    }

    /** A barista stops taking new chats (a chat in progress continues). */
    public void baristaOffline(@NotNull Actor actor) {
        requireNoTransaction();
        matchmaker.baristaOffline(requireBarista(actor));
    }

    /**
     * Ends a session.
     *
     * @return true if this call ended it, false if it was already over (idempotent)
     * @throws ChatSessionNotFoundException if there is no such session
     * @throws NotChatParticipantException  if the user is neither participant nor a MANAGER
     */
    public boolean endSession(@NotNull Actor actor, long sessionId) {
        requireNoTransaction();
        UserEntity user = requireUser(actor, ANYONE, "end a chat");
        SessionView session = requireSession(sessionId);
        requireParticipantOrManager(user, session, "end it");
        return matchmaker.end(sessionId);
    }

    /**
     * Posts a message (and, for a customer's {@code /order} command, places the order).
     *
     * @throws InvalidChatMessageException  if the content is null, blank (including only Unicode spaces) or over
     *                                      {@value #MAX_MESSAGE_LENGTH} characters (400)
     * @throws ChatSessionNotFoundException if there is no such session (404)
     * @throws NotChatParticipantException  if the user is not the session's customer or barista (403)
     * @throws SessionNotActiveException    if the session is WAITING or ended (409)
     */
    public SendResult sendMessage(@NotNull Actor actor, long sessionId, @Nullable String content) {
        requireNoTransaction();
        UserEntity sender = requireUser(actor, EnumSet.of(Role.CUSTOMER, Role.BARISTA), "post a chat message");
        String text = validContent(content);
        SessionView session = requireSession(sessionId);
        requireParticipant(sender, session, "post to it");
        if (session.status() != SessionStatus.ACTIVE) {
            throw new SessionNotActiveException(sessionId, session.status());
        }

        MessageView message = store.addMessage(sessionId, MessageType.CHAT_MESSAGE, sender.id(), sender.name(), text, null);
        if (sender.role() != Role.CUSTOMER) {
            return new SendResult(message, null, null);
        }
        return switch (parser.parse(text)) {
            case NotAnOrder ignored -> new SendResult(message, null, null);
            case MissingCoffeeType ignored -> refused(message, sessionId,
                    "Which coffee? Try: " + usage());
            case UnknownCoffeeType unknown -> refused(message, sessionId,
                    "Sorry, we don't serve '" + unknown.typed() + "'. Try: " + usage());
            case UnknownExtras unknown -> refused(message, sessionId,
                    "Sorry, unknown extra(s): " + String.join(", ", unknown.typed()) + ". Try: " + usage());
            case Parsed order -> placeOrder(message, sessionId, sender, order);
        };
    }

    /**
     * The session's messages, oldest first.
     *
     * @throws ChatSessionNotFoundException if there is no such session
     * @throws NotChatParticipantException  if the user is neither participant nor a MANAGER
     */
    public List<MessageView> history(@NotNull Actor actor, long sessionId) {
        UserEntity user = requireUser(actor, ANYONE, "read a chat");
        SessionView session = requireSession(sessionId);
        requireParticipantOrManager(user, session, "read it");
        return store.history(sessionId);
    }

    // ----- the /order path -------------------------------------------------------------------------------

    private SendResult placeOrder(MessageView message, long sessionId, UserEntity customer, Parsed command) {
        Order order;
        try {
            order = facade.placeOrder(new PlaceOrderRequest(customer.id(), command.coffee(), command.extras()));
        } catch (ShopClosedException e) {
            return refused(message, sessionId, "Sorry, the shop is closed right now, so your order was not placed.");
        } catch (CoffeeNotOnMenuException e) {
            return refused(message, sessionId, "Sorry, " + command.coffee().name().toLowerCase() + " is not on the menu today, so your order was not placed.");
        }
        String text = "Order #" + order.id() + " placed: " + order.coffeeDescription() + ", total " + order.price().total();
        MessageView reply = confirm(sessionId, text, order.id());
        return new SendResult(message, reply, order.id());
    }

    /** T3: the order already exists, so only the confirmation is retried, never the order. */
    private MessageView confirm(long sessionId, String text, Long orderId) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= CONFIRMATION_ATTEMPTS; attempt++) {
            try {
                return store.addMessage(sessionId, MessageType.SYSTEM_MESSAGE, null, ChatSessionStore.SYSTEM_SENDER, text, orderId);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Storing the confirmation for order {} failed (attempt {}/{})", orderId, attempt, CONFIRMATION_ATTEMPTS, e);
            }
        }
        log.error("Order {} was placed but its confirmation message could not be stored in session {}", orderId, sessionId, last);
        return null;
    }

    private SendResult refused(MessageView message, long sessionId, String explanation) {
        MessageView reply = store.addMessage(sessionId, MessageType.SYSTEM_MESSAGE, null, ChatSessionStore.SYSTEM_SENDER, explanation, null);
        return new SendResult(message, reply, null);
    }

    private String usage() {
        return "/order <" + String.join("|", parser.availableCoffees()) + "> [" + String.join(" ", parser.availableExtras()) + "]";
    }

    // ----- rules -----------------------------------------------------------------------------------------

    private static String validContent(@Nullable String content) {
        if (content == null || TextRules.isBlank(content)) {
            throw new InvalidChatMessageException("A chat message cannot be blank");
        }
        String text = TextRules.strip(content);
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidChatMessageException("A chat message is limited to " + MAX_MESSAGE_LENGTH + " characters");
        }
        return text;
    }

    /**
     * These methods are several independent transactions on purpose (chat messages, the order, its
     * confirmation). A caller that wraps them in one transaction would silently merge them, so a failure
     * in a later step could undo an order that was already reported placed. Fail fast instead.
     */
    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ChatService must not be called inside a transaction: its steps are separate transactions by design");
        }
    }

    private long requireBarista(Actor actor) {
        rejectSystem(actor);
        staffAccess.authorize(actor, BARISTAS);
        return actor.userId();
    }

    private UserEntity requireUser(Actor actor, Set<Role> allowed, String action) {
        rejectSystem(actor);
        UserEntity user = users.findById(actor.userId()).orElseThrow(() -> UnknownActorException.noSuchUser(actor.userId()));
        if (!allowed.contains(user.role())) {
            throw new RoleNotAllowedException(user.id(), user.role(), action);
        }
        return user;
    }

    private static void rejectSystem(Actor actor) {
        Objects.requireNonNull(actor, "actor cannot be null");
        if (actor.isSystem()) {
            throw new UnknownActorException("Chat needs a user; the system actor cannot chat");
        }
    }

    private SessionView requireSession(long sessionId) {
        return store.find(sessionId).orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
    }

    private static boolean isParticipant(UserEntity user, SessionView session) {
        return user.id() == session.customerId() || (session.baristaId() != null && user.id().equals(session.baristaId()));
    }

    private static void requireParticipant(UserEntity user, SessionView session, String action) {
        if (!isParticipant(user, session)) {
            throw new NotChatParticipantException(user.id(), session.id(), action);
        }
    }

    private static void requireParticipantOrManager(UserEntity user, SessionView session, String action) {
        if (user.role() != Role.MANAGER) {
            requireParticipant(user, session, action);
        }
    }
}
