package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.TestOrders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderStatusChangedEvent")
class OrderStatusChangedEventTest {

    @Nested
    @DisplayName("of()")
    class OfTests {

        @Test
        @DisplayName("captures the order id, customer id, previous status and current status")
        void capturesFields() {
            Order order = TestOrders.placedEspresso(42L, TestOrders.customer(7L));
            order.transitionTo(OrderStatus.PREPARING);

            OrderStatusChangedEvent event = OrderStatusChangedEvent.of(order, OrderStatus.PLACED);

            assertEquals(42L, event.orderId());
            assertEquals(7L, event.customerId());
            assertEquals(OrderStatus.PLACED, event.from());
            assertEquals(OrderStatus.PREPARING, event.to());
        }

        @Test
        @DisplayName("a just-placed order has a null 'from'")
        void nullFromOnPlacement() {
            Order order = TestOrders.placedEspresso(1L, TestOrders.customer(1L));
            OrderStatusChangedEvent event = OrderStatusChangedEvent.of(order, null);
            assertNull(event.from());
            assertEquals(OrderStatus.PLACED, event.to());
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null order id")
        void rejectsNullOrderId() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusChangedEvent(null, 1L, null, OrderStatus.PLACED, Instant.now()));
        }

        @Test
        @DisplayName("rejects a null target status")
        void rejectsNullTo() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusChangedEvent(1L, 1L, null, null, Instant.now()));
        }

        @Test
        @DisplayName("rejects a null timestamp")
        void rejectsNullAt() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusChangedEvent(1L, 1L, null, OrderStatus.PLACED, null));
        }
    }
}
