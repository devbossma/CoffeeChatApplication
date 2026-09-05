package dev.saberlabs.coffeechat.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The frozen price of an order: {@code base + extras - discount = total}.
 *
 * <p>Replaces {@code MyDesignPattern}'s single {@code finalPrice} double. Persisting the whole
 * breakdown (not just the total) is what lets a reordered clone be re-priced correctly and what
 * {@code CLAUDE.md}'s schema notes call for. Every component is money-scaled to 2 decimals and
 * the identity {@code base + extras - discount == total} is enforced at construction, so an
 * inconsistent breakdown can never exist.
 *
 * @param base     the base coffee cost before extras or discount
 * @param extras   the summed surcharge of all applied extras
 * @param discount the loyalty discount applied (a positive amount that is subtracted)
 * @param total    {@code base + extras - discount}
 */
public record PriceBreakdown(BigDecimal base, BigDecimal extras, BigDecimal discount, BigDecimal total) {

    public PriceBreakdown {
        base = scaled(base, "base");
        extras = scaled(extras, "extras");
        discount = scaled(discount, "discount");
        total = scaled(total, "total");

        BigDecimal expected = base.add(extras).subtract(discount);
        if (expected.compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    "Inconsistent price breakdown: base(%s) + extras(%s) - discount(%s) = %s, but total = %s"
                            .formatted(base, extras, discount, expected, total));
        }
    }

    /**
     * Builds a breakdown, computing {@code total} from the three components.
     */
    public static PriceBreakdown of(BigDecimal base, BigDecimal extras, BigDecimal discount) {
        BigDecimal b = scaled(base, "base");
        BigDecimal e = scaled(extras, "extras");
        BigDecimal d = scaled(discount, "discount");
        return new PriceBreakdown(b, e, d, b.add(e).subtract(d));
    }

    private static BigDecimal scaled(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative: " + value);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
