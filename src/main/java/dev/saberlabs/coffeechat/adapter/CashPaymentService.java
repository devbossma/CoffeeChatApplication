package dev.saberlabs.coffeechat.adapter;

import java.math.BigDecimal;

/**
 * Pattern 7: ADAPTER &mdash; Adaptee. A simulated cash register: works in dollars and returns
 * <em>change</em> (a {@link BigDecimal}), or a negative sentinel when the customer did not hand
 * over enough.
 */
public class CashPaymentService {

    static final BigDecimal INSUFFICIENT = BigDecimal.ONE.negate();

    /**
     * @param amountDue      what the order costs
     * @param amountTendered what the customer handed over
     * @return the change owed ({@code >= 0}), or {@link #INSUFFICIENT} if {@code amountTendered < amountDue}
     */
    public BigDecimal collectCash(BigDecimal amountDue, BigDecimal amountTendered) {
        if (amountTendered.compareTo(amountDue) < 0) {
            return INSUFFICIENT;
        }
        return amountTendered.subtract(amountDue);
    }
}
