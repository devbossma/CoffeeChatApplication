package dev.saberlabs.coffeechat.model;

/**
 * Customer loyalty tiers and their thresholds, carried over from {@code MyDesignPattern}
 * (PRD &sect;6):
 *
 * <table>
 *   <tr><th>Tier</th><th>Fulfilled orders</th><th>Discount</th></tr>
 *   <tr><td>{@code REGULAR}</td><td>0&ndash;5</td><td>0%</td></tr>
 *   <tr><td>{@code SILVER}</td><td>6&ndash;10</td><td>10%</td></tr>
 *   <tr><td>{@code GOLD}</td><td>11+</td><td>20%</td></tr>
 * </table>
 *
 * <p>The tier is always <em>derived</em> from the fulfilled-order count via {@link #forCount(long)}
 * &mdash; it is never stored as its own mutable field, so it cannot drift from the count
 * ({@code CLAUDE.md} &rarr; "one source of truth per fact"). The discount percentage itself lives
 * on the matching {@code PricingStrategy}, keyed by {@link #forCount(long)}'s result, not here.
 */
public enum LoyaltyTier {
    REGULAR,
    SILVER,
    GOLD;

    private static final int SILVER_THRESHOLD = 6;
    private static final int GOLD_THRESHOLD = 11;

    /**
     * Derives the tier for a given number of fulfilled orders.
     *
     * @param fulfilledOrders count of the customer's fulfilled orders; must not be negative
     * @return the tier that count maps to
     * @throws IllegalArgumentException if {@code fulfilledOrders} is negative
     */
    public static LoyaltyTier forCount(long fulfilledOrders) {
        if (fulfilledOrders < 0) {
            throw new IllegalArgumentException("fulfilledOrders cannot be negative: " + fulfilledOrders);
        }
        if (fulfilledOrders >= GOLD_THRESHOLD) {
            return GOLD;
        }
        if (fulfilledOrders >= SILVER_THRESHOLD) {
            return SILVER;
        }
        return REGULAR;
    }
}
