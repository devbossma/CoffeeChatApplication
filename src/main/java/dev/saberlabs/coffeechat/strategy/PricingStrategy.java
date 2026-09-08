package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;

import java.math.BigDecimal;

/**
 * Pattern 4: STRATEGY.
 *
 * <p>One implementation per {@link LoyaltyTier}, each encapsulating that tier's discount rule.
 * {@code Order} never sees an {@code if/else} over tiers &mdash; it is handed the right strategy.
 *
 * <p>Change from {@code MyDesignPattern}: instead of the {@code LoyaltyTier} enum owning a
 * {@code Supplier<PricingStrategy>}, each strategy declares the tier it serves via
 * {@link #supportedTier()} and {@code PricingStrategyResolver} builds the lookup. No bean-name
 * string matching, no per-tier qualifier (PRD &sect;11.2).
 */
public interface PricingStrategy {

    /** The loyalty tier this strategy prices for. */
    LoyaltyTier supportedTier();

    /**
     * @param baseCost the fully-decorated coffee cost, before any loyalty discount
     * @return the price to charge, scaled to 2 decimal places
     */
    BigDecimal priceFor(BigDecimal baseCost);

    /**
     * The discount amount this strategy takes off {@code baseCost}
     * (i.e. {@code baseCost - priceFor(baseCost)}).
     */
    default BigDecimal discountAmount(BigDecimal baseCost) {
        return baseCost.subtract(priceFor(baseCost));
    }
}
