package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.model.Order;

/**
 * Result of {@link CoffeeShopFacade#processOrder}: the order as it stands afterwards and the payment
 * outcome. When {@code payment} is not paid, the order was NOT fulfilled (it is still READY).
 */
public record OrderOutcome(Order order, PaymentResult payment) {

    public boolean fulfilled() {
        return payment.isPaid();
    }
}
