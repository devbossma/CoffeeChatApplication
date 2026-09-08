package dev.saberlabs.coffeechat.support;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.PriceBreakdown;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared builders for domain objects used across the pattern test suites, so each test class
 * does not re-declare the same fixture boilerplate.
 */
public final class TestOrders {

    private TestOrders() {
    }

    public static Customer customer(long id, String name, long fulfilledOrders) {
        Customer c = new Customer(name);
        c.assignId(id);
        for (long i = 0; i < fulfilledOrders; i++) {
            c.incrementFulfilled();
        }
        return c;
    }

    public static Customer customer(long id) {
        return customer(id, "Customer " + id, 0);
    }

    /** A plain-espresso {@link Order} with the given id, still at {@code null} status. */
    public static Order unplacedEspresso(long orderId, Customer customer) {
        Order order = new Order(
                customer,
                new Espresso(),
                CoffeeType.ESPRESSO,
                List.of(),
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR);
        order.assignId(orderId);
        return order;
    }

    /** A plain-espresso {@link Order} with the given id, already transitioned to {@code PLACED}. */
    public static Order placedEspresso(long orderId, Customer customer) {
        Order order = unplacedEspresso(orderId, customer);
        order.transitionTo(dev.saberlabs.coffeechat.model.OrderStatus.PLACED);
        return order;
    }
}
