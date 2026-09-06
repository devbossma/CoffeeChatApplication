/**
 * Pattern: STRATEGY.
 *
 * <p>One pricing strategy per loyalty tier (regular 0% / silver 10% / gold 20%), each its own
 * {@code @Component}. Rather than bean-name {@code Map} injection, each strategy declares the
 * tier it serves via {@code supportedTier()} and {@code PricingStrategyResolver} builds an
 * {@code EnumMap<LoyaltyTier, PricingStrategy>} once at construction (PRD &sect;11.2), failing
 * fast on a duplicate or missing tier.
 */
package dev.saberlabs.coffeechat.strategy;
