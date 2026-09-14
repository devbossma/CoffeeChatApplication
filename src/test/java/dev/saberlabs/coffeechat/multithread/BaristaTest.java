package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.support.TestOrders;
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

    private static Order order(long id) {
        return TestOrders.placedEspresso(id, TestOrders.customer(id));
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
            verify(facade).prepareOrder(1L);

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
            doThrow(new IllegalStateException("boom")).when(facade).prepareOrder(1L);
            org.mockito.Mockito.doAnswer(inv -> {
                secondPrepared.countDown();
                return null;
            }).when(facade).prepareOrder(2L);
            startLoop();

            orderQueue.enqueue(order(1L));
            orderQueue.enqueue(order(2L));

            assertTrue(secondPrepared.await(2, TimeUnit.SECONDS), "loop must survive the first order's failure");

            stopAndJoin();
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
            }).when(facade).prepareOrder(expectedId);
        }

        private void doAnswerRecording(CountDownLatch latch, List<Long> seen) {
            org.mockito.Mockito.doAnswer(inv -> {
                seen.add(inv.getArgument(0));
                latch.countDown();
                return null;
            }).when(facade).prepareOrder(org.mockito.ArgumentMatchers.anyLong());
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
