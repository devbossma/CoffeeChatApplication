package dev.saberlabs.coffeechat.adapter;

/**
 * Pattern 7: ADAPTER &mdash; Adaptee. A simulated Stripe API: talks in integer cents and
 * validates card details before charging. No real network call (PRD non-goal &sect;4).
 */
public class StripePaymentService {

    private final String cardNumber;
    private final String cvc;

    public StripePaymentService() {
        this("4242424242424242", "123");
    }

    public StripePaymentService(String cardNumber, String cvc) {
        this.cardNumber = cardNumber;
        this.cvc = cvc;
    }

    private boolean cardIsValid() {
        return cardNumber != null && cardNumber.matches("\\d{16}")
                && cvc != null && cvc.matches("\\d{3}");
    }

    /**
     * @return {@code true} if the card is valid and the amount is positive.
     */
    public boolean charge(long amountCents, String orderRef) {
        return cardIsValid() && amountCents > 0;
    }
}
