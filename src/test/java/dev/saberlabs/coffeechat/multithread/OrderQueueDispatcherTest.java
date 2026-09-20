package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderQueueDispatcher")
class OrderQueueDispatcherTest {

    private OrderQueue orderQueue;
    private OrderQueueDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        orderQueue = new OrderQueue(5);
        dispatcher = new OrderQueueDispatcher(orderQueue);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null OrderQueue")
        void rejectsNullOrderQueue() {
            assertThrows(NullPointerException.class, () -> new OrderQueueDispatcher(null));
        }
    }

    @Nested
    @DisplayName("onOrderStatusChanged()")
    class OnOrderStatusChangedTests {

        @Test
        @DisplayName("enqueues the order's id when it transitions to PLACED")
        void enqueuesOnPlaced() throws InterruptedException {
            dispatcher.onOrderStatusChanged(
                    new OrderStatusChangedEvent(42L, 7L, null, OrderStatus.PLACED, Instant.now()));

            assertEquals(42L, orderQueue.take());
        }

        @Test
        @DisplayName("does nothing for a non-PLACED transition")
        void ignoresOtherTransitions() {
            dispatcher.onOrderStatusChanged(new OrderStatusChangedEvent(
                    42L, 7L, OrderStatus.PLACED, OrderStatus.PREPARING, Instant.now()));

            assertTrue(orderQueue.isEmpty());
        }

        @Test
        @DisplayName("the same PLACED event delivered twice enqueues the id once")
        void duplicateDeliveryIsIdempotent() {
            var event = new OrderStatusChangedEvent(42L, 7L, null, OrderStatus.PLACED, Instant.now());
            dispatcher.onOrderStatusChanged(event);
            dispatcher.onOrderStatusChanged(event);

            assertEquals(1, orderQueue.size());
        }
    }
}
