package dev.saberlabs.coffeechat.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A customer's order: the (decorated) coffee, a frozen {@link PriceBreakdown}, the loyalty tier
 * that was applied when it was placed, and its position in the {@link OrderStatus} lifecycle.
 *
 * <p>Two things are frozen at placement and never recomputed afterwards, per {@code CLAUDE.md}'s
 * "one source of truth" table:
 * <ul>
 *   <li>{@link #appliedLoyaltyTier()} &mdash; the tier used to price <em>this</em> order, even
 *       if the customer's tier later changes;</li>
 *   <li>{@link #price()} &mdash; the full base/extras/discount/total breakdown.</li>
 * </ul>
 *
 * <p>{@link #baseType()} and {@link #extras()} keep the order's structure (not a flattened
 * string) so Prototype/reorder can rebuild an equivalent order.
 *
 * <p>Status starts {@code null}; {@code PlaceOrderCommand} moves it to {@link OrderStatus#PLACED}.
 * In Part 01 the {@code Order} lives only in {@code OrderService}'s in-memory map.
 */
public class Order {

    private Long id;
    private final Customer customer;
    private final Coffee coffee;
    private final CoffeeType baseType;
    private final List<ExtraType> extras;
    private final PriceBreakdown price;
    private final LoyaltyTier appliedLoyaltyTier;

    private OrderStatus status;
    private Instant placedAt;
    private Instant updatedAt;

    public Order(Customer customer,
                 Coffee coffee,
                 CoffeeType baseType,
                 List<ExtraType> extras,
                 PriceBreakdown price,
                 LoyaltyTier appliedLoyaltyTier) {
        this.customer = Objects.requireNonNull(customer, "customer cannot be null");
        this.coffee = Objects.requireNonNull(coffee, "coffee cannot be null");
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
        this.extras = List.copyOf(Objects.requireNonNull(extras, "extras cannot be null"));
        this.price = Objects.requireNonNull(price, "price cannot be null");
        this.appliedLoyaltyTier = Objects.requireNonNull(appliedLoyaltyTier, "appliedLoyaltyTier cannot be null");
        if (coffee.type() != baseType) {
            throw new IllegalArgumentException(
                    "coffee.type() (%s) does not match baseType (%s)".formatted(coffee.type(), baseType));
        }
    }

    /**
     * Assigns the store-generated id. Callable once.
     */
    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("Order id already assigned: " + this.id);
        }
        this.id = id;
    }

    /**
     * Moves the order to {@code target}, enforcing the legal lifecycle.
     *
     * <p>The only move allowed from the initial {@code null} status is to {@link OrderStatus#PLACED};
     * after that, {@link OrderStatus#canTransitionTo(OrderStatus)} governs.
     *
     * @throws IllegalStateException if the transition is not legal
     */
    public void transitionTo(OrderStatus target) {
        Objects.requireNonNull(target, "target status cannot be null");
        boolean legal = (status == null)
                ? target == OrderStatus.PLACED
                : status.canTransitionTo(target);
        if (!legal) {
            throw new IllegalStateException("Illegal order transition: " + status + " -> " + target);
        }
        Instant now = Instant.now();
        if (status == null) {
            this.placedAt = now;
        }
        this.status = target;
        this.updatedAt = now;
    }

    /**
     * Forces the status to {@code target}, bypassing the {@link #transitionTo(OrderStatus)}
     * legality check.
     *
     * <p>For the Command pattern's {@code undo()} only &mdash; reverting a lifecycle step means
     * moving "backwards", which the normal guard (correctly) forbids. {@code MyDesignPattern} has
     * the same escape hatch (its {@code restoreStatus}). {@code updatedAt} is refreshed;
     * {@code placedAt} is left alone.
     */
    public void restoreStatus(OrderStatus target) {
        this.status = Objects.requireNonNull(target, "target status cannot be null");
        this.updatedAt = Instant.now();
    }

    public Long id() {
        return id;
    }

    public Customer customer() {
        return customer;
    }

    public Coffee coffee() {
        return coffee;
    }

    public CoffeeType baseType() {
        return baseType;
    }

    /** The applied extras, in the order they were added. Unmodifiable. */
    public List<ExtraType> extras() {
        return extras;
    }

    public PriceBreakdown price() {
        return price;
    }

    public LoyaltyTier appliedLoyaltyTier() {
        return appliedLoyaltyTier;
    }

    /** Current lifecycle status, or {@code null} before the order has been placed. */
    public OrderStatus status() {
        return status;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Order[id=%s, customer=%s, coffee=%s, total=%s, tier=%s, status=%s]"
                .formatted(id, customer.name(), coffee.description(), price.total(), appliedLoyaltyTier, status);
    }
}
