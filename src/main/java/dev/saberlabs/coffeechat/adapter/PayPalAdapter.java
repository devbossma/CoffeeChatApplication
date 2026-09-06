package dev.saberlabs.coffeechat.adapter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pattern 7: ADAPTER &mdash; adapts {@link PayPalPaymentService} (integer cents, boolean result)
 * to {@link PaymentGateway} (dollars, {@link PaymentResult}).
 */
@Component
public class PayPalAdapter extends AbstractPaymentAdapter {

    private final PayPalPaymentService paypal;

    public PayPalAdapter() {
        this(new PayPalPaymentService());
    }

    public PayPalAdapter(PayPalPaymentService paypal) {
        this.paypal = paypal;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.PAYPAL;
    }

    @Override
    protected PaymentResult doPay(String orderRef, BigDecimal amount) {
        boolean ok = paypal.makePayment(toCents(amount), orderRef);
        return ok
                ? PaymentResult.paid(PaymentProvider.PAYPAL, orderRef, amount, "PayPal balance charged")
                : PaymentResult.failed(PaymentProvider.PAYPAL, orderRef, amount, "PayPal declined (insufficient balance)");
    }
}
