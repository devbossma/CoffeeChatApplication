/**
 * Pattern: STRATEGY.
 *
 * <p>One pricing strategy per loyalty tier (regular / silver / gold), each its own
 * {@code @Component}, injected as a {@code Map<LoyaltyTier, PricingStrategy>} rather than
 * selected with a conditional.
 */
package dev.saberlabs.coffeechat.strategy;
