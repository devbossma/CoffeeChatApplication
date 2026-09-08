package dev.saberlabs.coffeechat.prototype;

import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderPrototype")
class OrderPrototypeTest {

    private OrderPrototype prototype;

    @BeforeEach
    void setUp() {
        prototype = new OrderPrototype();
    }

    private static Order fulfilledOrder() {
        Customer customer = new Customer("Alice");
        customer.assignId(3L);
        Order order = new Order(
                customer, new Espresso(), CoffeeType.ESPRESSO, List.of(ExtraType.MILK),
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.50"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR);
        order.assignId(99L);
        order.transitionTo(OrderStatus.PLACED);
        order.transitionTo(OrderStatus.PREPARING);
        order.transitionTo(OrderStatus.READY);
        order.transitionTo(OrderStatus.FULFILLED);
        return order;
    }

    @Nested
    @DisplayName("copyOf()")
    class CopyOfTests {

        @Test
        @DisplayName("copies the customer id, base coffee type and extras")
        void copiesStructure() {
            prototype.copyOf(fulfilledOrder());
            assertEquals(3L, prototype.customerId());
            assertEquals(CoffeeType.ESPRESSO, prototype.type());
            assertEquals(List.of(ExtraType.MILK), prototype.extras());
        }

        @Test
        @DisplayName("does not carry over id, status or timestamps (they are not on the prototype at all)")
        void dropsIdentityAndLifecycle() {
            prototype.copyOf(fulfilledOrder());
            // the prototype only exposes structure; re-placing goes through placeOrder which
            // assigns a fresh id and PLACED status
            PlaceOrderRequest request = prototype.toPlaceOrderRequest();
            assertEquals(CoffeeType.ESPRESSO, request.type());
            assertEquals(List.of(ExtraType.MILK), request.extras());
        }

        @Test
        @DisplayName("takes an independent copy of the extras list")
        void independentExtras() {
            Order original = fulfilledOrder();
            prototype.copyOf(original);
            assertThrows(UnsupportedOperationException.class,
                    () -> prototype.extras().add(ExtraType.SUGAR));
        }

        @Test
        @DisplayName("rejects a null order")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> prototype.copyOf(null));
        }
    }

    @Nested
    @DisplayName("toPlaceOrderRequest()")
    class ToPlaceOrderRequestTests {

        @Test
        @DisplayName("builds a request matching the seeded order")
        void buildsRequest() {
            prototype.copyOf(fulfilledOrder());
            PlaceOrderRequest request = prototype.toPlaceOrderRequest();
            assertEquals(3L, request.customerId());
            assertEquals(CoffeeType.ESPRESSO, request.type());
        }

        @Test
        @DisplayName("throws if the prototype has not been seeded")
        void throwsWhenNotSeeded() {
            assertThrows(IllegalStateException.class, () -> prototype.toPlaceOrderRequest());
        }

        @Test
        @DisplayName("a fresh prototype has empty extras")
        void freshHasEmptyExtras() {
            assertTrue(prototype.extras().isEmpty());
        }
    }
}
