package dev.saberlabs.coffeechat.model;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a persisted order &mdash; a plain value type handed to controllers, the
 * Prototype, and tests. It is never mutated and never written back: the only mutable, persisted
 * representation of an order is {@code OrderEntity}, and every status change goes through it inside a
 * transaction. Built by {@code OrderMapper} inside the read transaction so lazy state (the extras
 * collection) is copied out while the session is open.
 *
 * <p>{@code coffeeDescription} is derived (Factory + Decorator rebuilt from {@code baseType} and
 * {@code extras}), never stored.
 */
public record Order(Long id,
                    Long customerId,
                    CoffeeType baseType,
                    List<ExtraType> extras,
                    String coffeeDescription,
                    PriceBreakdown price,
                    LoyaltyTier appliedLoyaltyTier,
                    OrderStatus status,
                    Instant placedAt,
                    Instant updatedAt) {

    public Order(@NotNull Long id,
                 @NotNull Long customerId,
                 @NotNull CoffeeType baseType,
                 @NotNull List<ExtraType> extras,
                 @NotNull String coffeeDescription,
                 @NotNull PriceBreakdown price,
                 @NotNull LoyaltyTier appliedLoyaltyTier,
                 @NotNull OrderStatus status,
                 @NotNull Instant placedAt,
                 @NotNull Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
        this.baseType = Objects.requireNonNull(baseType, "baseType cannot be null");
        this.extras = List.copyOf(Objects.requireNonNull(extras, "extras cannot be null"));
        this.coffeeDescription = Objects.requireNonNull(coffeeDescription, "coffeeDescription cannot be null");
        this.price = Objects.requireNonNull(price, "price cannot be null");
        this.appliedLoyaltyTier = Objects.requireNonNull(appliedLoyaltyTier, "appliedLoyaltyTier cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.placedAt = Objects.requireNonNull(placedAt, "placedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
