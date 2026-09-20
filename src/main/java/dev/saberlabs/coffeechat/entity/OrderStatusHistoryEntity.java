package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.OrderStatus;
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
 * The durable order-lifecycle audit trail, written by the one {@code OrderStatusChangedEvent}
 * listener dedicated to this concern ({@code OrderStatusHistoryListener}) &mdash; a third,
 * independent listener alongside {@code OrderNotificationListener} and
 * {@code OrderQueueDispatcher}, same precedent Part 02 already set: CLAUDE.md's "one listener"
 * rule is scoped to notifications specifically, not to every possible reaction to the event.
 * {@code OrderInvoker}'s in-memory command history stays a lightweight recent-activity/undo aid,
 * never this.
 *
 * <p>{@link #fromStatus} is {@code null} for the first row on an order (its {@code PLACED}
 * transition has no prior status). {@link #changedBy} is {@code null} for an automated/system
 * transition (the async barista consumer loops are threads, not user accounts); when set, the
 * application layer must have already confirmed the referenced user's role is {@code BARISTA}
 * &mdash; a plain FK cannot check the referenced row's role, so that rule lives in code
 * (Part 03 Step 4), not here.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private UserEntity changedBy;

    /** For JPA/Hibernate only. */
    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(@NotNull OrderEntity order,
                                    @Nullable OrderStatus fromStatus,
                                    @NotNull OrderStatus toStatus,
                                    @NotNull Instant changedAt,
                                    @Nullable UserEntity changedBy) {
        this.order = Objects.requireNonNull(order, "order cannot be null");
        this.fromStatus = fromStatus;
        this.toStatus = Objects.requireNonNull(toStatus, "toStatus cannot be null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt cannot be null");
        this.changedBy = changedBy;
    }

    public Long id() {
        return id;
    }

    public OrderEntity order() {
        return order;
    }

    public OrderStatus fromStatus() {
        return fromStatus;
    }

    public OrderStatus toStatus() {
        return toStatus;
    }

    public Instant changedAt() {
        return changedAt;
    }

    public UserEntity changedBy() {
        return changedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof OrderStatusHistoryEntity other && id != null && id.equals(other.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "OrderStatusHistoryEntity[id=%s, order=%s, %s -> %s, changedBy=%s]"
                .formatted(id, order.id(), fromStatus, toStatus, changedBy == null ? "system" : changedBy.id());
    }
}
