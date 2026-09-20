package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * One message in a {@link ChatSessionEntity}. {@link #sender} is {@code null} for a
 * {@code SYSTEM_MESSAGE} (no fake "System" user row needed). {@link #senderName} is kept as its
 * own persisted column even though it is derivable from {@link #sender} for a human message:
 * chat messages are historical records, and a later display-name change should not rewrite what
 * an old message shows.
 *
 * <p>{@link #content} is capped at 2000 characters at the DB level
 * ({@code chk_chat_message_content_length}); the matching {@code @Size(max = 2000)} lives on the
 * Part 03 Step 6 REST request DTO, not here.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MessageType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private UserEntity sender;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderEntity order;

    /** For JPA/Hibernate only. */
    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(@NotNull ChatSessionEntity session,
                             @NotNull MessageType type,
                             @Nullable UserEntity sender,
                             @NotNull String senderName,
                             @NotNull String content,
                             @NotNull Instant sentAt,
                             @Nullable OrderEntity order) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.sender = sender;
        this.senderName = Objects.requireNonNull(senderName, "senderName cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt cannot be null");
        this.order = order;
    }

    public Long id() {
        return id;
    }

    public ChatSessionEntity session() {
        return session;
    }

    public MessageType type() {
        return type;
    }

    public UserEntity sender() {
        return sender;
    }

    public String senderName() {
        return senderName;
    }

    public String content() {
        return content;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public OrderEntity order() {
        return order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ChatMessageEntity other && id != null && id.equals(other.id());
    }

    /** A constant, not {@code Objects.hashCode(id)} -- see {@code UserEntity.hashCode()}'s javadoc. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Never dereferences {@link #session} beyond a proxy-initialization check -- see {@code OrderEntity.toString()}'s javadoc. */
    @Override
    public String toString() {
        String sessionDescription = Hibernate.isInitialized(session) ? String.valueOf(session.id()) : "<lazy>";
        return "ChatMessageEntity[id=%s, session=%s, type=%s, sender=%s]"
                .formatted(id, sessionDescription, type, senderName);
    }
}
