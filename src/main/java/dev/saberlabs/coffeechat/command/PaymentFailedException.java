package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.PaymentResult;

/**
 * Thrown by {@link PayOrderCommand} when the payment gateway declines. Carries the failing
 * {@link PaymentResult} for the caller to inspect / surface.
 */
public class PaymentFailedException extends RuntimeException {

    private final transient PaymentResult result;

    public PaymentFailedException(PaymentResult result) {
        super("Payment failed via " + result.provider() + ": " + result.detail());
        this.result = result;
    }

    public PaymentResult result() {
        return result;
    }
}
