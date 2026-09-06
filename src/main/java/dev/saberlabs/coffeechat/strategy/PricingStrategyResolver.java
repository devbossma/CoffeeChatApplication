package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link LoyaltyTier} &rarr; {@link PricingStrategy} lookup once, from every
 * {@code PricingStrategy} bean Spring injects, keyed by each strategy's own
 * {@link PricingStrategy#supportedTier()} (PRD &sect;11.2).
 *
 * <p>Construction fails fast if two strategies claim the same tier, or if any tier has no
 * strategy &mdash; a misconfiguration should stop the context from starting, not surface as a
 * missing discount at runtime.
 */
@Component
public class PricingStrategyResolver {

    private final Map<LoyaltyTier, PricingStrategy> byTier;

    public PricingStrategyResolver(List<PricingStrategy> strategies) {
        this.byTier = new EnumMap<>(LoyaltyTier.class);
        for (PricingStrategy strategy : strategies) {
            PricingStrategy previous = byTier.put(strategy.supportedTier(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two pricing strategies claim tier " + strategy.supportedTier() + ": "
                                + previous.getClass().getSimpleName() + " and "
                                + strategy.getClass().getSimpleName());
            }
        }
        for (LoyaltyTier tier : LoyaltyTier.values()) {
            if (!byTier.containsKey(tier)) {
                throw new IllegalStateException("No pricing strategy registered for tier " + tier);
            }
        }
    }

    /**
     * @param tier the customer's (derived) loyalty tier
     * @return the strategy that prices for that tier
     * @throws IllegalArgumentException if {@code tier} is null
     */
    public PricingStrategy forTier(LoyaltyTier tier) {
        if (tier == null) {
            throw new IllegalArgumentException("Loyalty tier cannot be null");
        }
        return byTier.get(tier);
    }
}
