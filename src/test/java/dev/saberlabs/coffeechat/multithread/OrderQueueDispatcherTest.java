package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderStatusChangedEvent;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.support.TestOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderQueueDispatcher")
class OrderQueueDispatcherTest {

    private OrderService orders;
    private OrderQueue orderQueue;
    private OrderQueueDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        orders = new OrderService();
        orderQueue = new OrderQueue(5);
        dispatcher = new OrderQueueDispatcher(orderQueue, orders);
    }

    @Nested
    @DisplayName("onOrderStatusChanged()")
    class OnOrderStatusChangedTests {

        @Test
        @DisplayName("enqueues the order when it transitions to PLACED")
        void enqueuesOnPlaced() throws InterruptedException {
            Order order = TestOrders.unplacedEspresso(0, TestOrders.customer(1L));
            order = orders.save(order);
            order.transitionTo(OrderStatus.PLACED);

            dispatcher.onOrderStatusChanged(
                    new OrderStatusChangedEvent(order.id(), order.customer().id(), null, OrderStatus.PLACED, Instant.now()));

            assertSame(order, orderQueue.take());
        }

        @Test
        @DisplayName("does nothing for a non-PLACED transition")
        void ignoresOtherTransitions() {
            Order order = orders.save(TestOrders.unplacedEspresso(0, TestOrders.customer(1L)));

            dispatcher.onOrderStatusChanged(new OrderStatusChangedEvent(
                    order.id(), order.customer().id(), OrderStatus.PLACED, OrderStatus.PREPARING, Instant.now()));

            assertTrue(orderQueue.isEmpty());
        }

        @Test
        @DisplayName("does not throw when the referenced order is not found")
        void toleratesMissingOrder() {
            assertDoesNotThrow(() -> dispatcher.onOrderStatusChanged(
                    new OrderStatusChangedEvent(404L, 1L, null, OrderStatus.PLACED, Instant.now())));
            assertTrue(orderQueue.isEmpty());
        }
    }
}
