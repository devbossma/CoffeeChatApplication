package dev.saberlabs.coffeechat.adapter;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pattern 7: ADAPTER &mdash; adapts {@link CashPaymentService} (dollars in, change out) to
 * {@link PaymentGateway} (a {@link PaymentResult}).
 *
 * <p>Simulation: the customer tenders the next whole dollar up, so there is always change and
 * the payment always clears &mdash; an insufficient-cash outcome only exists at the
 * {@link CashPaymentService} level (a real register would be handed an arbitrary amount).
 */
@Component
public class CashPaymentAdapter extends AbstractPaymentAdapter {

    private final CashPaymentService register;

    public CashPaymentAdapter() {
        this(new CashPaymentService());
    }

    public CashPaymentAdapter(CashPaymentService register) {
        this.register = register;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.CASH;
    }

    @Override
    protected PaymentResult doPay(String orderRef, BigDecimal amount) {
        BigDecimal tendered = amount.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal change = register.collectCash(amount, tendered);
        if (change.signum() < 0) {
            return PaymentResult.failed(PaymentProvider.CASH, orderRef, amount, "Insufficient cash tendered");
        }
        return PaymentResult.paid(PaymentProvider.CASH, orderRef, amount, "change $" + change);
    }
}
