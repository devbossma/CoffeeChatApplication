package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import dev.saberlabs.coffeechat.support.TestGateways;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two threads, one order, real transactions and a real row: the guarantees that only exist across
 * threads (charged once, counted once, exactly one writer wins) proven with a barrier, not sleeps.
 */
@DisplayName("Order concurrency")
class OrderConcurrencyTest extends AbstractIntegrationTest {

    /** Runs {@code n} tasks released together by a barrier; returns each task's outcome (value or exception). */
    private static <T> List<Object> raceTogether(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            CyclicBarrier start = new CyclicBarrier(n);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long failures(List<Object> outcomes) {
        return outcomes.stream().filter(o -> o instanceof Exception).count();
    }

    private Long readyOrderId(CoffeeShopFacade facade, UserEntity customer) {
        Order placed = facade.placeOrder(new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of()));
        facade.prepareOrder(placed.id(), Actor.SYSTEM);
        return placed.id();
    }

    /** True when some other database session is blocked waiting for a row lock ({@code FOR UPDATE}). */
    private boolean aSessionIsBlockedOnARowLock() {
        Integer blocked = jdbc.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query ILIKE '%for%update%'",
                Integer.class);
        return blocked != null && blocked > 0;
    }

    @Nested
    @DisplayName("two threads paying the same order")
    class DoublePayTests {

        @Test
        @DisplayName("the customer is charged once, and the two payers PROVABLY overlapped: the first is held inside the gateway until the second is blocked on the row lock")
        void chargedOnce() throws Exception {
            AtomicInteger charges = new AtomicInteger();
            java.util.concurrent.atomic.AtomicBoolean overlapObserved = new java.util.concurrent.atomic.AtomicBoolean();
            CoffeeShopFacade facade = facadeWith(TestGateways.countingCash(charges, () -> {
                // Runs inside the gateway call: the lock holder waits here until a second session is
                // genuinely blocked on the order's row lock, so the test cannot pass by the payers
                // simply running one after the other.
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15))
                        .until(OrderConcurrencyTest.this::aSessionIsBlockedOnARowLock);
                overlapObserved.set(true);
            }));
            Long id = readyOrderId(facade, customer("Alice"));

            List<Object> outcomes = raceTogether(2, () -> facade.payOrder(id, PaymentProvider.CASH, Actor.SYSTEM));

            assertTrue(overlapObserved.get(), "a second payer was blocked on the lock while the first was charging");
            assertEquals(1, failures(outcomes), "exactly one payer is rejected");
            Object loser = outcomes.stream().filter(o -> o instanceof Exception).findFirst().orElseThrow();
            assertTrue(loser instanceof OrderStateConflictException, "the loser is told the order is already paid: " + loser);
            assertEquals(1, charges.get(), "the gateway is called exactly once");
            assertEquals(1, payments.count());
        }
    }

    @Nested
    @DisplayName("two threads fulfilling the same order")
    class DoubleFulfilTests {

        @Test
        @DisplayName("counts once: fulfilled_orders is 1, one FULFILLED history row, one thread wins and the other fails")
        void countedOnce() throws Exception {
            CoffeeShopFacade facade = facadeWith(TestGateways.countingCash(new AtomicInteger()));
            UserEntity customer = customer("Alice");
            Long id = readyOrderId(facade, customer);
            facade.payOrder(id, PaymentProvider.CASH, Actor.SYSTEM);

            List<Object> outcomes = raceTogether(2, () -> {
                facade.fulfillOrder(id, Actor.SYSTEM);
                return "fulfilled";
            });

            assertEquals(1, failures(outcomes), "exactly one fulfilment wins: " + outcomes);
            Object loser = outcomes.stream().filter(o -> o instanceof Exception).findFirst().orElseThrow();
            assertTrue(loser instanceof org.springframework.dao.OptimisticLockingFailureException
                            || loser instanceof dev.saberlabs.coffeechat.model.IllegalOrderTransitionException,
                    "the loser lost the version race or found it already fulfilled: " + loser);
            assertEquals(1, fulfilledOrdersOf(customer.id()));
            assertEquals(OrderStatus.FULFILLED, facade.getOrder(id).status());
            assertEquals(1, history.findByOrderIdOrderByChangedAtAscIdAsc(id).stream()
                    .filter(r -> r.toStatus() == OrderStatus.FULFILLED).count());
        }
    }

    @Nested
    @DisplayName("a REST cancel racing a barista prepare on the same PLACED order")
    class CancelVersusPrepareTests {

        @Test
        @DisplayName("whatever the interleaving, the final status is legal, no unexpected error occurs, and the audit trail is continuous and matches the stored status")
        void consistentWhateverTheInterleaving() throws Exception {
            CoffeeShopFacade facade = facadeWith(TestGateways.countingCash(new AtomicInteger()));
            Order placed = facade.placeOrder(new PlaceOrderRequest(customer("Alice").id(), CoffeeType.ESPRESSO, List.of()));
            AtomicInteger which = new AtomicInteger();

            List<Object> outcomes = raceTogether(2, () -> {
                if (which.getAndIncrement() == 0) {
                    facade.cancelOrder(placed.id(), Actor.SYSTEM);
                } else {
                    facade.prepareOrder(placed.id(), Actor.SYSTEM);
                }
                return "done";
            });

            // Both succeeding is legal (prepare, then cancel a READY order); a failure must be one of
            // the two expected losing-a-race errors, never anything else.
            assertTrue(outcomes.stream().filter(o -> o instanceof Exception).allMatch(o ->
                            o instanceof org.springframework.dao.OptimisticLockingFailureException
                                    || o instanceof dev.saberlabs.coffeechat.model.IllegalOrderTransitionException),
                    "unexpected failure type in " + outcomes);
            assertTrue(outcomes.stream().anyMatch(o -> !(o instanceof Exception)), "at least one of the two succeeded");

            OrderStatus finalStatus = facade.getOrder(placed.id()).status();
            assertTrue(finalStatus == OrderStatus.CANCELLED || finalStatus == OrderStatus.READY,
                    "final status " + finalStatus + " outcomes " + outcomes);
            var trail = history.findByOrderIdOrderByChangedAtAscIdAsc(placed.id());
            assertEquals(finalStatus, trail.get(trail.size() - 1).toStatus(), "audit agrees with state");
            for (int i = 1; i < trail.size(); i++) {
                assertEquals(trail.get(i - 1).toStatus(), trail.get(i).fromStatus(), "audit is continuous");
            }
        }
    }
}
