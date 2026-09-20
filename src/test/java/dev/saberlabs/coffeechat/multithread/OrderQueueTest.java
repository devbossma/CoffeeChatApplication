package dev.saberlabs.coffeechat.multithread;

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

    /** The queue carries order ids, so a "queued order" in these tests is just its id. */
    private static Long order(long id) {
        return id;
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
            Long order = order(1L);
            queue.enqueue(order);
            assertEquals(1, queue.size());
            assertEquals(order, queue.take());
        }

        @Test
        @DisplayName("rejects a null order id")
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
            AtomicReference<Long> received = new AtomicReference<>();
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

            Long order = order(1L);
            queue.enqueue(order);
            consumer.join(2000);

            assertEquals(order, received.get());
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
    @DisplayName("enqueue() dedupe")
    class DedupeTests {

        @Test
        @DisplayName("an id that is already waiting is not enqueued a second time")
        void duplicateIgnored() {
            OrderQueue queue = new OrderQueue(5);
            queue.enqueue(1L);
            queue.enqueue(1L);
            assertEquals(1, queue.size());
        }

        @Test
        @DisplayName("distinct ids are all kept, in order")
        void distinctIdsKept() throws InterruptedException {
            OrderQueue queue = new OrderQueue(5);
            queue.enqueue(1L);
            queue.enqueue(2L);
            assertEquals(1L, queue.take());
            assertEquals(2L, queue.take());
        }

        @Test
        @DisplayName("an id taken by a consumer can be legitimately re-enqueued (the retry path) -- a stale entry never blocks it")
        void reEnqueueAfterTake() throws InterruptedException {
            OrderQueue queue = new OrderQueue(5);
            queue.enqueue(1L);
            queue.take();

            queue.enqueue(1L);

            assertEquals(1, queue.size());
            assertTrue(queue.contains(1L));
        }

        @Test
        @DisplayName("an id removed by poll() can be re-enqueued too")
        void reEnqueueAfterPoll() throws InterruptedException {
            OrderQueue queue = new OrderQueue(5);
            queue.enqueue(1L);
            queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);

            queue.enqueue(1L);

            assertEquals(1, queue.size());
        }

        @Test
        @DisplayName("an enqueue interrupted while waiting for space does not leave a phantom entry that blocks a later enqueue")
        void interruptedEnqueueLeavesNoPhantom() throws InterruptedException {
            OrderQueue queue = new OrderQueue(1);
            queue.enqueue(1L);
            Thread producer = new Thread(() -> {
                try {
                    queue.enqueue(2L);
                } catch (IllegalStateException expected) {
                    // interrupted while blocked on a full queue
                }
            });
            producer.start();
            org.awaitility.Awaitility.await().until(() -> producer.getState() == Thread.State.WAITING);
            producer.interrupt();
            producer.join(2000);

            queue.take();
            queue.enqueue(2L);

            assertTrue(queue.contains(2L));
        }
    }

    @Nested
    @DisplayName("contains()")
    class ContainsTests {

        @Test
        @DisplayName("reports whether an id is currently waiting")
        void reportsMembership() {
            OrderQueue queue = new OrderQueue(3);
            queue.enqueue(7L);
            assertTrue(queue.contains(7L));
            assertFalse(queue.contains(8L));
        }
    }

    @Nested
    @DisplayName("poll()")
    class PollTests {

        @Test
        @DisplayName("returns the next order immediately when one is available")
        void returnsAvailableOrder() throws InterruptedException {
            OrderQueue queue = new OrderQueue(3);
            Long order = order(1L);
            queue.enqueue(order);

            assertEquals(order, queue.poll(1, java.util.concurrent.TimeUnit.SECONDS));
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
