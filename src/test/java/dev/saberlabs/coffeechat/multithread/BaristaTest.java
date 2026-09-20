package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.model.OrderStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Barista")
class BaristaTest {

    private OrderQueue orderQueue;
    private CoffeeShopFacade facade;
    private Barista barista;
    private Thread loopThread;

    @BeforeEach
    void setUp() {
        orderQueue = new OrderQueue(5);
        facade = mock(CoffeeShopFacade.class);
        barista = new Barista(orderQueue, facade);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null OrderQueue")
        void rejectsNullOrderQueue() {
            assertThrows(NullPointerException.class, () -> new Barista(null, facade));
        }

        @Test
        @DisplayName("rejects a null CoffeeShopFacade")
        void rejectsNullFacade() {
            assertThrows(NullPointerException.class, () -> new Barista(orderQueue, null));
        }
    }

    /** Runs consumeLoop() on a plain thread, bypassing the @Async proxy (unit-test scope). */
    private void startLoop() {
        loopThread = new Thread(barista::consumeLoop, "test-barista");
        loopThread.start();
    }

    private void stopAndJoin() throws InterruptedException {
        barista.shutdown();
        loopThread.interrupt();
        loopThread.join(2000);
    }

    private static Long order(long id) {
        return id;
    }

    @Nested
    @DisplayName("consumeLoop()")
    class ConsumeLoopTests {

        @Test
        @DisplayName("prepares each dequeued order through the facade")
        void preparesDequeuedOrders() throws InterruptedException {
            CountDownLatch prepared = new CountDownLatch(1);
            doAnswerCountDown(prepared, 1L);
            startLoop();

            orderQueue.enqueue(order(1L));

            assertTrue(prepared.await(2, TimeUnit.SECONDS));
            verify(facade).prepareOrder(1L, Actor.SYSTEM);

            stopAndJoin();
        }

        @Test
        @DisplayName("processes multiple orders in sequence without stopping")
        void processesMultipleOrders() throws InterruptedException {
            CountDownLatch prepared = new CountDownLatch(3);
            List<Long> seen = new CopyOnWriteArrayList<>();
            doAnswerRecording(prepared, seen);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));
            orderQueue.enqueue(order(3L));

            assertTrue(prepared.await(2, TimeUnit.SECONDS));
            assertEquals(List.of(1L, 2L, 3L), seen);

            stopAndJoin();
        }

        @Test
        @DisplayName("a failure preparing one order does not stop the loop")
        void continuesAfterFailure() throws InterruptedException {
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new RuntimeException("boom")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            org.mockito.Mockito.doAnswer(inv -> {
                secondPrepared.countDown();
                return null;
            }).when(facade).prepareOrder(2L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS), "loop must survive the first order's failure");

            stopAndJoin();
        }

        @Test
        @DisplayName("a lost @Version race is retried and the order is then prepared")
        void retriesOnOptimisticConflict() throws InterruptedException {
            CountDownLatch prepared = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(inv -> {
                if (calls.incrementAndGet() == 1) {
                    throw new OptimisticLockingFailureException("stale");
                }
                prepared.countDown();
                return null;
            }).when(facade).prepareOrder(1L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));

            assertTrue(prepared.await(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
            stopAndJoin();
        }

        @Test
        @DisplayName("keeps losing the @Version race: retried MAX_ATTEMPTS times per round, then re-queued, and later orders are still served")
        void conflictsAreRequeuedNotDropped() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new OptimisticLockingFailureException("stale")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                    verify(facade, org.mockito.Mockito.times(Barista.MAX_ATTEMPTS * Barista.MAX_UNEXPECTED_ATTEMPTS))
                            .prepareOrder(1L, Actor.SYSTEM));
            stopAndJoin();
        }

        @Test
        @DisplayName("an unrelated IllegalStateException (e.g. the publisher's no-transaction error) is NOT swallowed as a quiet skip: it is treated as unexpected and retried")
        void unrelatedIllegalStateIsNotHidden() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            CountDownLatch prepared = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(inv -> {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("An order status change must be published inside a transaction");
                }
                prepared.countDown();
                return null;
            }).when(facade).prepareOrder(1L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));

            assertTrue(prepared.await(3, TimeUnit.SECONDS), "the order was retried, not dropped as an INFO skip");
            assertEquals(2, calls.get());
            stopAndJoin();
        }

        @Test
        @DisplayName("if the retry scheduler is already shut down, the failure is logged and the loop survives to serve later orders")
        void schedulerShutDownDoesNotKillTheLoop() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            barista.closeRetryScheduler();
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new RuntimeException("hiccup")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS), "the loop is still alive");
            assertTrue(loopThread.isAlive());
            stopAndJoin();
        }

        @Test
        @DisplayName("a failure while re-enqueueing (queue full and interrupted) is logged, not swallowed silently, and does not kill anything")
        void failedReenqueueDoesNotKillAnything() throws InterruptedException {
            OrderQueue tiny = new OrderQueue(1);
            barista = new Barista(tiny, facade, 5);
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new RuntimeException("hiccup")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            tiny.enqueue(1L);
            org.awaitility.Awaitility.await().until(tiny::isEmpty);
            tiny.enqueue(2L); // fills the queue so the scheduled re-enqueue of order 1 has to wait for space

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            stopAndJoin();
        }

        @Test
        @DisplayName("an unexpected failure twice, then success: the order is re-queued each time and finally prepared")
        void unexpectedFailureRequeuedThenSucceeds() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            CountDownLatch prepared = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(inv -> {
                if (calls.incrementAndGet() <= 2) {
                    throw new RuntimeException("database hiccup");
                }
                prepared.countDown();
                return null;
            }).when(facade).prepareOrder(1L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));

            assertTrue(prepared.await(3, TimeUnit.SECONDS));
            assertEquals(3, calls.get());
            stopAndJoin();
        }

        @Test
        @DisplayName("an order that always fails unexpectedly gets exactly MAX_UNEXPECTED_ATTEMPTS attempts, is then left alone, and the loop keeps serving")
        void alwaysFailingIsBounded() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new RuntimeException("always broken")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() ->
                    verify(facade, org.mockito.Mockito.times(Barista.MAX_UNEXPECTED_ATTEMPTS)).prepareOrder(1L, Actor.SYSTEM));
            // no further retry is scheduled after giving up: still exactly MAX_UNEXPECTED_ATTEMPTS a moment later
            org.awaitility.Awaitility.await().pollDelay(java.time.Duration.ofMillis(300)).atMost(java.time.Duration.ofSeconds(2))
                    .untilAsserted(() -> verify(facade, org.mockito.Mockito.times(Barista.MAX_UNEXPECTED_ATTEMPTS)).prepareOrder(1L, Actor.SYSTEM));
            assertTrue(orderQueue.isEmpty());
            stopAndJoin();
        }

        @Test
        @DisplayName("after giving up, the same id starts again with a fresh attempt budget (a later recovery re-enqueue is not penalised)")
        void budgetResetsAfterGivingUp() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            doThrow(new RuntimeException("always broken")).when(facade).prepareOrder(1L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() ->
                    verify(facade, org.mockito.Mockito.times(Barista.MAX_UNEXPECTED_ATTEMPTS)).prepareOrder(1L, Actor.SYSTEM));
            org.awaitility.Awaitility.await().until(orderQueue::isEmpty);

            orderQueue.enqueue(order(1L));

            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3)).untilAsserted(() ->
                    verify(facade, org.mockito.Mockito.times(2 * Barista.MAX_UNEXPECTED_ATTEMPTS)).prepareOrder(1L, Actor.SYSTEM));
            stopAndJoin();
        }

        @Test
        @DisplayName("a re-queue never duplicates an id that is already waiting (dedupe set respected)")
        void requeueRespectsDedupe() throws InterruptedException {
            barista = new Barista(orderQueue, facade, 10);
            CountDownLatch prepared = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(inv -> {
                if (calls.incrementAndGet() == 1) {
                    orderQueue.enqueue(order(1L)); // recovery re-enqueues while the failed attempt is in flight
                    throw new RuntimeException("hiccup");
                }
                prepared.countDown();
                return null;
            }).when(facade).prepareOrder(1L, Actor.SYSTEM);
            startLoop();

            orderQueue.enqueue(order(1L));

            assertTrue(prepared.await(3, TimeUnit.SECONDS));
            stopAndJoin();
            assertTrue(orderQueue.size() <= 1);
        }

        @Test
        @DisplayName("an order that is not found (e.g. an id that was never visible) is skipped without retrying")
        void skipsMissingOrder() throws InterruptedException {
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new OrderNotFoundException(1L)).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            verify(facade, org.mockito.Mockito.times(1)).prepareOrder(1L, Actor.SYSTEM);
            stopAndJoin();
        }

        @Test
        @DisplayName("an order that is no longer preparable (illegal transition) is skipped without retrying")
        void skipsNoLongerPreparableOrder() throws InterruptedException {
            CountDownLatch secondPrepared = new CountDownLatch(1);
            doThrow(new dev.saberlabs.coffeechat.model.IllegalOrderTransitionException(OrderStatus.CANCELLED, OrderStatus.PREPARING)).when(facade).prepareOrder(1L, Actor.SYSTEM);
            doAnswerCountDown(secondPrepared, 2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS));
            verify(facade, org.mockito.Mockito.times(1)).prepareOrder(1L, Actor.SYSTEM);
            stopAndJoin();
        }

        @Test
        @DisplayName("an idle loop exits promptly on shutdown() alone, with no interrupt (the timed poll notices the flag)")
        void idleLoopExitsWithoutInterrupt() throws InterruptedException {
            startLoop();
            Thread.sleep(50);

            barista.shutdown();
            loopThread.join(Barista.POLL_TIMEOUT_MS * 5);

            assertTrue(!loopThread.isAlive(), "idle loop must notice the stop flag within a poll interval");
        }

        @Test
        @DisplayName("shutdown() combined with an interrupt stops the loop")
        void shutdownStopsLoop() throws InterruptedException {
            startLoop();
            assertTrue(barista.isRunning());

            stopAndJoin();

            assertTrue(!loopThread.isAlive(), "loop thread should have exited");
        }

        private void doAnswerCountDown(CountDownLatch latch, long expectedId) {
            org.mockito.Mockito.doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(facade).prepareOrder(expectedId, Actor.SYSTEM);
        }

        private void doAnswerRecording(CountDownLatch latch, List<Long> seen) {
            org.mockito.Mockito.doAnswer(inv -> {
                seen.add(inv.getArgument(0));
                latch.countDown();
                return null;
            }).when(facade).prepareOrder(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("constructor (retry delay)")
    class RetryDelayConstructorTests {

        @Test
        @DisplayName("rejects a negative retry delay")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new Barista(orderQueue, facade, -1));
        }
    }

    @Nested
    @DisplayName("restart()")
    class RestartTests {

        @Test
        @DisplayName("re-arms a barista that was shut down")
        void rearms() {
            barista.shutdown();
            assertTrue(!barista.isRunning());
            barista.restart();
            assertTrue(barista.isRunning());
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("flips isRunning() to false")
        void flipsRunning() {
            assertTrue(barista.isRunning());
            barista.shutdown();
            assertTrue(!barista.isRunning());
        }
    }
}
