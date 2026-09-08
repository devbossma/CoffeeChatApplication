package dev.saberlabs.coffeechat.adapter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pattern 7: ADAPTER &mdash; adapts {@link StripePaymentService} (integer cents, card
 * validation, boolean result) to {@link PaymentGateway}. Prefixes the order reference with
 * {@code "STRIPE-"} the way {@code MyDesignPattern}'s adapter did.
 */
@Component
public class StripeAdapter extends AbstractPaymentAdapter {

    private final StripePaymentService stripe;

    public StripeAdapter() {
        this(new StripePaymentService());
    }

    public StripeAdapter(StripePaymentService stripe) {
        this.stripe = stripe;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    protected PaymentResult doPay(String orderRef, BigDecimal amount) {
        String stripeRef = "STRIPE-" + orderRef;
        boolean ok = stripe.charge(toCents(amount), stripeRef);
        return ok
                ? PaymentResult.paid(PaymentProvider.STRIPE, orderRef, amount, "Stripe charge " + stripeRef)
                : PaymentResult.failed(PaymentProvider.STRIPE, orderRef, amount, "Stripe declined (card invalid)");
    }
}
