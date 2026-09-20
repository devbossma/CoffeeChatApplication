package dev.saberlabs.coffeechat.support;

import dev.saberlabs.coffeechat.adapter.CashPaymentAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalPaymentService;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.adapter.StripeAdapter;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Gateway resolvers for tests that need a payment to decline, or need to count real charges. */
public final class TestGateways {

    private TestGateways() {
    }

    /** PayPal has a $0.01 balance, so every real charge on it declines; Stripe and Cash behave normally. */
    public static PaymentGatewayResolver decliningPayPal() {
        return new PaymentGatewayResolver(List.of(
                new PayPalAdapter(new PayPalPaymentService(1L)), new StripeAdapter(), new CashPaymentAdapter()));
    }

    /** Cash works normally but every charge that reaches the gateway increments {@code charges}. */
    public static PaymentGatewayResolver countingCash(AtomicInteger charges) {
        return countingCash(charges, () -> { });
    }

    /**
     * As {@link #countingCash(AtomicInteger)}, but {@code beforeCharge} runs inside the gateway call
     * (i.e. while the caller holds its transaction and the order's row lock), which lets a test hold the
     * first payer there until it can prove a second payer is waiting.
     */
    public static PaymentGatewayResolver countingCash(AtomicInteger charges, Runnable beforeCharge) {
        return new PaymentGatewayResolver(List.of(new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter() {
            @Override
            protected PaymentResult doPay(String orderRef, BigDecimal amount) {
                charges.incrementAndGet();
                beforeCharge.run();
                return super.doPay(orderRef, amount);
            }
        }));
    }
}
