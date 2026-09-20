package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.support.TestOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderQueue")
class OrderQueueTest {

    private static Order order(long id) {
        return TestOrders.placedEspresso(id, TestOrders.customer(id));
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("starts empty with the configured capacity free")
        void startsEmpty() {
            OrderQueue queue = new OrderQueue(5);
            assertTrue(queue.isEmpty());
            assertEquals(0, queue.size());
            assertEquals(5, queue.remainingCapacity());
        }

        @Test
        @DisplayName("rejects a zero capacity")
        void rejectsZeroCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new OrderQueue(0));
        }

        @Test
        @DisplayName("rejects a negative capacity")
        void rejectsNegativeCapacity() {
            assertThrows(IllegalArgumentException.class, () -> new OrderQueue(-1));
        }
    }

    @Nested
    @DisplayName("enqueue()")
    class EnqueueTests {

        @Test
        @DisplayName("adds an order that take() then returns")
        void addsOrder() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            Order order = order(1L);
            queue.enqueue(order);
            assertEquals(1, queue.size());
            assertSame(order, queue.take());
        }

        @Test
        @DisplayName("rejects a null order")
        void rejectsNull() {
            OrderQueue queue = new OrderQueue(3);
            assertThrows(NullPointerException.class, () -> queue.enqueue(null));
        }

        @Test
        @DisplayName("blocks the caller while the queue is at capacity, until space frees up")
        void blocksWhenFull() throws InterruptedException {
            OrderQueue queue = new OrderQueue(1);
            queue.enqueue(order(1L));

            AtomicBoolean secondEnqueueReturned = new AtomicBoolean(false);
            Thread producer = new Thread(() -> {
                queue.enqueue(order(2L));
                secondEnqueueReturned.set(true);
            });
            producer.start();

            Thread.sleep(150);
            assertFalse(secondEnqueueReturned.get(), "enqueue should still be blocked while full");

            queue.take(); // frees a slot
            producer.join(2000);
            assertTrue(secondEnqueueReturned.get(), "enqueue should complete once space is available");
        }

        @Test
        @DisplayName("restores the interrupt flag and throws IllegalStateException when interrupted while blocked")
        void restoresInterruptOnInterruption() throws InterruptedException {
            OrderQueue queue = new OrderQueue(1);
            queue.enqueue(order(1L)); // fill it so the next enqueue blocks

            AtomicReference<Exception> thrown = new AtomicReference<>();
            AtomicBoolean interruptedAfterCatch = new AtomicBoolean(false);
            Thread producer = new Thread(() -> {
                try {
                    queue.enqueue(order(2L));
                } catch (IllegalStateException e) {
                    thrown.set(e);
                    interruptedAfterCatch.set(Thread.currentThread().isInterrupted());
                }
            });
            producer.start();
            Thread.sleep(150);
            producer.interrupt();
            producer.join(2000);

            assertNotNull(thrown.get(), "enqueue should have thrown when interrupted");
            assertTrue(interruptedAfterCatch.get(), "interrupt status should be restored before throwing");
        }
    }

    @Nested
    @DisplayName("take()")
    class TakeTests {

        @Test
        @DisplayName("blocks the caller until an order is enqueued, then returns it")
        void blocksUntilAvailable() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            AtomicReference<Order> received = new AtomicReference<>();
            Thread consumer = new Thread(() -> {
                try {
                    received.set(queue.take());
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            consumer.start();

            Thread.sleep(150);
            assertNull(received.get());

            Order order = order(1L);
            queue.enqueue(order);
            consumer.join(2000);

            assertSame(order, received.get());
        }

        @Test
        @DisplayName("propagates interruption while blocked on an empty queue")
        void propagatesInterruption() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            AtomicBoolean interrupted = new AtomicBoolean(false);
            Thread consumer = new Thread(() -> {
                try {
                    queue.take();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            });
            consumer.start();
            Thread.sleep(150);
            consumer.interrupt();
            consumer.join(2000);

            assertTrue(interrupted.get());
        }
    }

    @Nested
    @DisplayName("poll()")
    class PollTests {

        @Test
        @DisplayName("returns the next order immediately when one is available")
        void returnsAvailableOrder() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            Order order = order(1L);
            queue.enqueue(order);

            assertSame(order, queue.poll(1, java.util.concurrent.TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("returns null once the timeout elapses on an empty queue")
        void returnsNullOnTimeout() throws InterruptedException {
            assertNull(new OrderQueue(3).poll(20, java.util.concurrent.TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("propagates interruption while waiting")
        void propagatesInterruption() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            AtomicBoolean interrupted = new AtomicBoolean(false);
            Thread consumer = new Thread(() -> {
                try {
                    queue.poll(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            });
            consumer.start();
            Thread.sleep(150);
            consumer.interrupt();
            consumer.join(2000);

            assertTrue(interrupted.get());
        }

        @Test
        @DisplayName("rejects a null TimeUnit")
        void rejectsNullUnit() {
            assertThrows(NullPointerException.class, () -> new OrderQueue(3).poll(1, null));
        }
    }

    @Nested
    @DisplayName("size() / isEmpty() / remainingCapacity()")
    class StateTests {

        private OrderQueue queue;

        @BeforeEach
        void setUp() {
            queue = new OrderQueue(2);
        }

        @Test
        @DisplayName("reflect the number of orders currently held")
        void reflectCurrentState() {
            queue.enqueue(order(1L));
            assertEquals(1, queue.size());
            assertFalse(queue.isEmpty());
            assertEquals(1, queue.remainingCapacity());
        }
    }
}
