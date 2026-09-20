package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A persisted order. Mirrors {@code model.Order}'s invariants (frozen
 * {@link #appliedLoyaltyTier}, frozen {@link PriceBreakdown}, structured extras) but is a
 * separate class &mdash; see the package javadoc for why.
 *
 * <p>The constructor takes a {@link PriceBreakdown}, not four loose {@code BigDecimal}s: that
 * value type already computes the discount once, at scale 2, with a fixed {@code RoundingMode}
 * (HALF_UP), and enforces {@code base + extras - discount = total} in its compact constructor.
 * Building an {@code OrderEntity} from one guarantees {@code chk_order_price_consistent} can
 * never reject a legitimately-built order &mdash; there is no second, independent computation of
 * {@code total} anywhere in this class.
 *
 * <p>{@link #extras} is a {@code List}, not a {@code Set}: duplicates are meaningful (two milks
 * costs twice what one does) and order is preserved via {@code @OrderColumn} so a Prototype
 * reorder can reproduce the exact original description.
 *
 * <p>{@link #version} backs optimistic locking: async barista threads and REST requests can touch
 * the same order row concurrently (Part 03 Step 3). No other entity in this schema needs one
 * &mdash; see {@code UserEntity}'s javadoc for why {@code fulfilledOrders} doesn't, and
 * {@code PaymentEntity}/{@code OrderStatusHistoryEntity} are insert-once/insert-only.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private UserEntity customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_coffee_type", nullable = false, length = 20)
    private CoffeeType baseCoffeeType;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_extras", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "extra_index")
    @Enumerated(EnumType.STRING)
    @Column(name = "extra_type", nullable = false, length = 20)
    private List<ExtraType> extras = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_loyalty_tier", nullable = false, length = 20)
    private LoyaltyTier appliedLoyaltyTier;

    @Column(name = "price_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceBase;

    @Column(name = "price_extras", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceExtras;

    @Column(name = "price_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDiscount;

    @Column(name = "price_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceTotal;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** For JPA/Hibernate only. */
    protected OrderEntity() {
    }

    public OrderEntity(@NotNull UserEntity customer,
                       @NotNull CoffeeType baseCoffeeType,
                       @NotNull List<ExtraType> extras,
                       @NotNull OrderStatus status,
                       @NotNull LoyaltyTier appliedLoyaltyTier,
                       @NotNull PriceBreakdown price,
                       @NotNull Instant placedAt,
                       @NotNull Instant updatedAt) {
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.baseCoffeeType = Objects.requireNonNull(baseCoffeeType, "baseCoffeeType cannot be null");
        this.extras = new ArrayList<>(Objects.requireNonNull(extras, "extras cannot be null"));
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.appliedLoyaltyTier = Objects.requireNonNull(appliedLoyaltyTier, "appliedLoyaltyTier cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        this.priceBase = price.base();
        this.priceExtras = price.extras();
        this.priceDiscount = price.discount();
        this.priceTotal = price.total();
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public Long id() {
        return id;
    }

    public UserEntity customer() {
        return customer;
    }

    public CoffeeType baseCoffeeType() {
        return baseCoffeeType;
    }

    public List<ExtraType> extras() {
        return List.copyOf(extras);
    }

    public OrderStatus status() {
        return status;
    }

    public void status(OrderStatus status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public LoyaltyTier appliedLoyaltyTier() {
        return appliedLoyaltyTier;
    }

    public PriceBreakdown price() {
        return new PriceBreakdown(priceBase, priceExtras, priceDiscount, priceTotal);
    }

    public Instant placedAt() {
        return placedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void updatedAt(Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public int version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof OrderEntity other && id != null && id.equals(other.id());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "OrderEntity[id=%s, customer=%s, coffee=%s, total=%s, status=%s, version=%d]"
                .formatted(id, customer.id(), baseCoffeeType, priceTotal, status, version);
    }
}
