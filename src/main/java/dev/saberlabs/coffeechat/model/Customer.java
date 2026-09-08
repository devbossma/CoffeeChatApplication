package dev.saberlabs.coffeechat.model;

import java.util.Objects;

/**
 * A coffee-shop customer.
 *
 * <p>Only the fulfilled-order <em>count</em> is stored; {@link #loyaltyTier()} derives the tier
 * on every read via {@link LoyaltyTier#forCount(long)}, so the two can never disagree
 * ({@code CLAUDE.md} &rarr; "one source of truth per fact").
 *
 * <p>In Part 01 this is a plain in-memory object handed out by {@code CustomerService}; in
 * Part 03 it becomes a JPA {@code @Entity}. The id is {@code null} until the store assigns one.
 */
public class Customer {

    private Long id;
    private final String name;
    private long fulfilledOrders;

    public Customer(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer name cannot be blank");
        }
        this.name = name;
        this.fulfilledOrders = 0;
    }

    /**
     * Assigns the store-generated id. Callable once; a second call is a programming error.
     */
    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("Customer id already assigned: " + this.id);
        }
        this.id = id;
    }

    /** Records one more fulfilled order &mdash; may bump the derived {@link #loyaltyTier()}. */
    public void incrementFulfilled() {
        fulfilledOrders++;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long fulfilledOrders() {
        return fulfilledOrders;
    }

    /** The tier derived from {@link #fulfilledOrders()} &mdash; never stored, always current. */
    public LoyaltyTier loyaltyTier() {
        return LoyaltyTier.forCount(fulfilledOrders);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Customer other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Customer[id=%s, name=%s, fulfilledOrders=%d, tier=%s]"
                .formatted(id, name, fulfilledOrders, loyaltyTier());
    }
}
