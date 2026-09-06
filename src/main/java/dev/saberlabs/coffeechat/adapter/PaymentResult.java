package dev.saberlabs.coffeechat.adapter;

import java.math.BigDecimal;

/**
 * The outcome of a payment attempt, normalised across providers.
 *
 * @param provider which back-end handled it
 * @param orderRef the order reference that was paid
 * @param amount   the amount charged, in dollars
 * @param status   {@link PaymentStatus#PAID} or {@link PaymentStatus#FAILED}
 * @param detail   a short human-readable note (e.g. {@code "change $0.20"}, {@code "card declined"})
 */
public record PaymentResult(PaymentProvider provider,
                            String orderRef,
                            BigDecimal amount,
                            PaymentStatus status,
                            String detail) {

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    static PaymentResult paid(PaymentProvider provider, String orderRef, BigDecimal amount, String detail) {
        return new PaymentResult(provider, orderRef, amount, PaymentStatus.PAID, detail);
    }

    static PaymentResult failed(PaymentProvider provider, String orderRef, BigDecimal amount, String detail) {
        return new PaymentResult(provider, orderRef, amount, PaymentStatus.FAILED, detail);
    }
}
