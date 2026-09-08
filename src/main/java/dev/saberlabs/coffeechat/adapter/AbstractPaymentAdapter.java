package dev.saberlabs.coffeechat.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared plumbing for the adapters: argument validation and the dollars &rarr; cents conversion
 * two of the three adaptees need.
 */
abstract class AbstractPaymentAdapter implements PaymentGateway {

    @Override
    public final PaymentResult pay(String orderRef, BigDecimal amount) {
        if (orderRef == null || orderRef.isBlank()) {
            throw new IllegalArgumentException("orderRef cannot be blank");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        return doPay(orderRef, amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** @param amount already validated (&gt; 0) and scaled to 2 decimals. */
    protected abstract PaymentResult doPay(String orderRef, BigDecimal amount);

    protected static long toCents(BigDecimal dollars) {
        return dollars.movePointRight(2).longValueExact();
    }
}
