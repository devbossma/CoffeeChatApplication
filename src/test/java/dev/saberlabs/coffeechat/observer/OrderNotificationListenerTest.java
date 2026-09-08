package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderNotificationListener")
class OrderNotificationListenerTest {

    private OrderNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderNotificationListener();
    }

    private static OrderStatusChangedEvent event(long orderId, OrderStatus to) {
        return new OrderStatusChangedEvent(orderId, 1L, null, to, Instant.now());
    }

    @Nested
    @DisplayName("onOrderStatusChanged()")
    class OnOrderStatusChangedTests {

        @Test
        @DisplayName("records a customer-facing message for the order")
        void recordsMessage() {
            listener.onOrderStatusChanged(event(5L, OrderStatus.PLACED));
            assertEquals(1, listener.notificationsFor(5L).size());
            assertTrue(listener.notificationsFor(5L).get(0).contains("placed"));
        }

        @Test
        @DisplayName("the message reflects the new status")
        void messageReflectsStatus() {
            listener.onOrderStatusChanged(event(5L, OrderStatus.READY));
            assertTrue(listener.latestFor(5L).contains("ready for pickup"));
        }

        @Test
        @DisplayName("accumulates one message per event, in order")
        void accumulates() {
            listener.onOrderStatusChanged(event(5L, OrderStatus.PLACED));
            listener.onOrderStatusChanged(event(5L, OrderStatus.PREPARING));
            listener.onOrderStatusChanged(event(5L, OrderStatus.READY));
            assertEquals(3, listener.notificationsFor(5L).size());
            assertTrue(listener.latestFor(5L).contains("ready"));
        }

        @Test
        @DisplayName("keeps different orders' notifications separate")
        void separatesOrders() {
            listener.onOrderStatusChanged(event(1L, OrderStatus.PLACED));
            listener.onOrderStatusChanged(event(2L, OrderStatus.CANCELLED));
            assertTrue(listener.latestFor(1L).contains("placed"));
            assertTrue(listener.latestFor(2L).contains("cancelled"));
        }

        @Test
        @DisplayName("covers every status with a distinct message")
        void everyStatusMapped() {
            for (OrderStatus status : OrderStatus.values()) {
                listener.onOrderStatusChanged(event(99L, status));
            }
            assertEquals(OrderStatus.values().length, listener.notificationsFor(99L).size());
        }
    }

    @Nested
    @DisplayName("notificationsFor()")
    class NotificationsForTests {

        @Test
        @DisplayName("returns an empty list for an order with no notifications")
        void emptyForUnknown() {
            assertTrue(listener.notificationsFor(404L).isEmpty());
        }

        @Test
        @DisplayName("returns an unmodifiable copy")
        void unmodifiableCopy() {
            listener.onOrderStatusChanged(event(5L, OrderStatus.PLACED));
            assertThrows(UnsupportedOperationException.class,
                    () -> listener.notificationsFor(5L).add("tamper"));
        }
    }

    @Nested
    @DisplayName("latestFor()")
    class LatestForTests {

        @Test
        @DisplayName("null when the order has had no notifications")
        void nullWhenNone() {
            assertNull(listener.latestFor(404L));
        }
    }

    @Nested
    @DisplayName("clear()")
    class ClearTests {

        @Test
        @DisplayName("drops all recorded notifications")
        void dropsAll() {
            listener.onOrderStatusChanged(event(5L, OrderStatus.PLACED));
            listener.clear();
            assertTrue(listener.notificationsFor(5L).isEmpty());
        }
    }
}
