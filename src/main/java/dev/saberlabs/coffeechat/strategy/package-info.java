/**
 * Pattern: STRATEGY.
 *
 * <p>One pricing strategy per loyalty tier (regular / silver / gold), each its own
 * {@code @Component}, injected as a {@code Map<LoyaltyTier, PricingStrategy>} rather than
 * selected with a conditional. See {@code PRD.md} section 7.2, row 4.
 */
package dev.saberlabs.coffeechat.strategy;
