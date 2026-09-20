package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.SessionStatus;
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
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * A chat conversation between one customer and, once matched, one barista. {@code BaristaQueue}
 * (in-memory FIFO matching, Part 03 Step 5) is the only writer of {@link #status} &mdash; the
 * one-source-of-truth rule from CLAUDE.md.
 *
 * <p>{@code uq_chat_sessions_active_customer} (a partial unique index on {@code customer_id}
 * where {@code status <> 'INACTIVE'}) enforces at most one non-INACTIVE session per customer at
 * the DB level, alongside the equivalent app-level check in {@code ChatService.startChat()}.
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserEntity customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barista_id")
    private UserEntity barista;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA/Hibernate only. */
    protected ChatSessionEntity() {
    }

    public ChatSessionEntity(@NotNull UserEntity customer,
                             @Nullable UserEntity barista,
                             @NotNull SessionStatus status,
                             @NotNull Instant createdAt) {
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.barista = barista;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public Long id() {
        return id;
    }

    public UserEntity customer() {
        return customer;
    }

    public UserEntity barista() {
        return barista;
    }

    public void barista(@Nullable UserEntity barista) {
        this.barista = barista;
    }

    public SessionStatus status() {
        return status;
    }

    public void status(@NotNull SessionStatus status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ChatSessionEntity other && id != null && id.equals(other.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ChatSessionEntity[id=%s, customer=%s, barista=%s, status=%s]"
                .formatted(id, customer.id(), barista == null ? null : barista.id(), status);
    }
}
