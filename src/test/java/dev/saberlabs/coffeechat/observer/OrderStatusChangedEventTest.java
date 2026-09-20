package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.TestEntities;
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
            OrderEntity order = TestEntities.placedEspresso(42L, TestEntities.customer(7L));
            order.transitionTo(OrderStatus.PREPARING);

            OrderStatusChangedEvent event = OrderStatusChangedEvent.of(order, OrderStatus.PLACED, null);

            assertEquals(42L, event.orderId());
            assertEquals(7L, event.customerId());
            assertEquals(OrderStatus.PLACED, event.from());
            assertEquals(OrderStatus.PREPARING, event.to());
        }

        @Test
        @DisplayName("records the actor when one is supplied, and none for a system transition")
        void recordsActor() {
            OrderEntity order = TestEntities.placedEspresso(42L, TestEntities.customer(7L));
            assertEquals(9L, OrderStatusChangedEvent.of(order, null, 9L).actorUserId());
            assertNull(OrderStatusChangedEvent.of(order, null, null).actorUserId());
        }

        @Test
        @DisplayName("the five-argument constructor is a system (actor-less) transition")
        void fiveArgIsSystem() {
            OrderStatusChangedEvent event = new OrderStatusChangedEvent(1L, 1L, null, OrderStatus.PLACED, Instant.now());
            assertNull(event.actorUserId());
        }

        @Test
        @DisplayName("a just-placed order has a null 'from'")
        void nullFromOnPlacement() {
            OrderEntity order = TestEntities.placedEspresso(1L, TestEntities.customer(1L));
            OrderStatusChangedEvent event = OrderStatusChangedEvent.of(order, null, null);
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
