package dev.saberlabs.coffeechat.adapter;

/**
 * Pattern 7: ADAPTER &mdash; Adaptee. A simulated PayPal API: talks in integer cents and returns
 * a bare boolean. No real network call (PRD non-goal &sect;4).
 */
public class PayPalPaymentService {

    private final long balanceCents;

    public PayPalPaymentService() {
        this(100_000_00L);
    }

    public PayPalPaymentService(long balanceCents) {
        this.balanceCents = balanceCents;
    }

    /**
     * @return {@code true} if the account can cover {@code amountCents}.
     */
    public boolean makePayment(long amountCents, String reference) {
        return amountCents > 0 && amountCents <= balanceCents;
    }
}
