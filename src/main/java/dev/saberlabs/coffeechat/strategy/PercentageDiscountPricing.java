package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared base for the three tier strategies: charge {@code baseCost * (1 - rate)}, rounded to
 * cents. Each concrete strategy just declares its {@link LoyaltyTier} and its discount rate.
 */
abstract class PercentageDiscountPricing implements PricingStrategy {

    private final LoyaltyTier tier;
    private final BigDecimal rate;

    protected PercentageDiscountPricing(LoyaltyTier tier, BigDecimal rate) {
        this.tier = tier;
        this.rate = rate;
    }

    @Override
    public LoyaltyTier supportedTier() {
        return tier;
    }

    @Override
    public BigDecimal priceFor(BigDecimal baseCost) {
        return baseCost.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
    }
}
