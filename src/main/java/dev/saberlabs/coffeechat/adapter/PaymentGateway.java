package dev.saberlabs.coffeechat.adapter;

import java.math.BigDecimal;

/**
 * Pattern 7: ADAPTER &mdash; the Target interface.
 *
 * <p>The single payment API the coffee shop depends on: pay a dollar amount against an order
 * reference and get back a normalised {@link PaymentResult}. Each concrete adapter bridges one
 * incompatible third-party service ({@code PayPalPaymentService}, {@code StripePaymentService},
 * {@code CashPaymentService}) to this, and declares which {@link PaymentProvider} it is so
 * {@link PaymentGatewayResolver} can route to it.
 */
public interface PaymentGateway {

    /** The provider this adapter fronts. */
    PaymentProvider provider();

    /**
     * Charges {@code amount} dollars for {@code orderRef}.
     *
     * @param orderRef a non-blank order reference
     * @param amount   a strictly positive dollar amount
     * @return the outcome, never null
     * @throws IllegalArgumentException if {@code orderRef} is blank or {@code amount} is null or &le; 0
     */
    PaymentResult pay(String orderRef, BigDecimal amount);
}
