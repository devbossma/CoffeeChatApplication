package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.Hibernate;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One payment per order ({@code UNIQUE(order_id)}, enforced here via {@code @JoinColumn(unique)}
 * and in the schema). This entity owns the relationship &mdash; {@code OrderEntity} carries no
 * inverse {@code @OneToOne}, since nothing needs "give me the order for this payment" in reverse;
 * {@code PaymentRepository.findByOrderId} covers the direction actually used.
 *
 * <p>Inserted exactly once, after the payment gateway responds, with {@link #status} already
 * {@code PAID} or {@code FAILED} &mdash; not written as {@code PENDING} first and updated later.
 * {@code PaymentFailedException} today never leaves a row behind at all; a future decision to
 * record failed attempts is a schema-compatible follow-up, not a blocker now. {@code PENDING}
 * remains a valid enum value for forward-compatibility.
 *
 * <p>{@link #amount} must equal the paid order's {@code price().total()} &mdash; a CHECK
 * constraint can't reference another table, so Part 03 Step 3 enforces this in
 * {@code PayOrderCommand} before this entity is ever built.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA/Hibernate only. */
    protected PaymentEntity() {
    }

    public PaymentEntity(@NotNull OrderEntity order,
                         @NotNull PaymentProvider provider,
                         @NotNull BigDecimal amount,
                         @NotNull PaymentStatus status,
                         @Nullable String detail,
                         @NotNull Instant createdAt,
                         @NotNull Instant updatedAt) {
        this.order = Objects.requireNonNull(order, "order cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.detail = detail;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public Long id() {
        return id;
    }

    public OrderEntity order() {
        return order;
    }

    public PaymentProvider provider() {
        return provider;
    }

    public BigDecimal amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof PaymentEntity other && id != null && id.equals(other.id());
    }

    /** A constant, not {@code Objects.hashCode(id)} -- see {@code UserEntity.hashCode()}'s javadoc. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Never dereferences {@link #order} beyond a proxy-initialization check -- see {@code OrderEntity.toString()}'s javadoc. */
    @Override
    public String toString() {
        String orderDescription = Hibernate.isInitialized(order) ? String.valueOf(order.id()) : "<lazy>";
        return "PaymentEntity[id=%s, order=%s, provider=%s, amount=%s, status=%s]"
                .formatted(id, orderDescription, provider, amount, status);
    }
}
