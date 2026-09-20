package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.service.StaffAccess;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.command.CancelOrderCommand;
import dev.saberlabs.coffeechat.command.OrderCommand;
import dev.saberlabs.coffeechat.command.OrderInvoker;
import dev.saberlabs.coffeechat.command.PrepareOrderCommand;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.multithread.OrderQueueDispatcher;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event/phase design, proven against real transactions: the audit row shares the status
 * change's transaction (it cannot disagree with state), while notification and queueing happen only
 * after commit (a barista can never dequeue an uncommitted order).
 */
@DisplayName("Order lifecycle events: phases and history")
class OrderLifecycleEventsTest extends AbstractIntegrationTest {

    @Autowired CoffeeShopFacade facade;
    @Autowired OrderInvoker invoker;
    @Autowired OrderService orderService;
    @Autowired OrderEventPublisher events;
    @Autowired CoffeePreparationResolver preparations;
    @Autowired OrderStatusHistoryListener historyListener;
    @Autowired TransactionTemplate tx;
    @Autowired StaffAccess staffAccess;

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));

    private Order place(UserEntity customer) {
        return facade.placeOrder(new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of()));
    }

    private List<OrderStatusHistoryEntity> trail(Long orderId) {
        return history.findByOrderIdOrderByChangedAtAscIdAsc(orderId);
    }

    /** Reads the number of committed orders from a DIFFERENT thread, i.e. a different connection. */
    private long committedOrderCountSeenByAnotherThread() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            return other.submit(() -> orders.count()).get();
        } finally {
            other.shutdownNow();
        }
    }

    @Nested
    @DisplayName("OrderStatusHistoryListener (same transaction as the status change)")
    class HistoryTests {

        @Test
        @DisplayName("placing an order writes one history row: null -> PLACED, changed_by NULL")
        void placementRow() {
            Order order = place(customer("Alice"));

            List<OrderStatusHistoryEntity> rows = trail(order.id());

            assertEquals(1, rows.size());
            assertNull(rows.get(0).fromStatus());
            assertEquals(OrderStatus.PLACED, rows.get(0).toStatus());
            assertNull(rows.get(0).changedBy());
        }

        @Test
        @DisplayName("preparing writes PLACED -> PREPARING then PREPARING -> READY, in write order")
        void prepareRows() {
            Order order = place(customer("Alice"));

            facade.prepareOrder(order.id(), Actor.SYSTEM);

            List<OrderStatusHistoryEntity> rows = trail(order.id());
            assertEquals(List.of(OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY),
                    rows.stream().map(OrderStatusHistoryEntity::toStatus).toList());
            assertEquals(OrderStatus.PLACED, rows.get(1).fromStatus());
            assertEquals(OrderStatus.PREPARING, rows.get(2).fromStatus());
        }

        @Test
        @DisplayName("the audit trail agrees with the order's stored status after a full lifecycle, and every actor is NULL in Step 3")
        void auditAgreesWithState() {
            Order order = place(customer("Alice"));

            facade.processOrder(order.id(), PaymentProvider.CASH, Actor.SYSTEM);

            List<OrderStatusHistoryEntity> rows = trail(order.id());
            OrderStatus lastRecorded = rows.get(rows.size() - 1).toStatus();
            assertEquals(orders.findById(order.id()).orElseThrow().status(), lastRecorded);
            assertEquals(OrderStatus.FULFILLED, lastRecorded);
            for (int i = 1; i < rows.size(); i++) {
                assertEquals(rows.get(i - 1).toStatus(), rows.get(i).fromStatus(), "each row continues the previous");
            }
            assertTrue(rows.stream().allMatch(r -> r.changedBy() == null));
        }

        @Test
        @DisplayName("a command that throws after the transition rolls back BOTH the status change and its history row")
        void rollbackTakesTheAuditWithIt() {
            Order order = place(customer("Alice"));
            OrderCommand cancelThenFail = new OrderCommand() {
                private final CancelOrderCommand cancel = new CancelOrderCommand(order.id(), orderService, events, Actor.SYSTEM, staffAccess);
                @Override public void execute() {
                    cancel.execute();
                    throw new IllegalStateException("boom after the transition");
                }
                @Override public void undo() { }
                @Override public String name() { return "CancelThenFail"; }
            };

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(cancelThenFail));

            assertEquals(OrderStatus.PLACED, orders.findById(order.id()).orElseThrow().status());
            assertEquals(1, trail(order.id()).size(), "only the original placement row survives");
            assertEquals("Order " + order.id() + " has been placed", notifications.latestFor(order.id()));
        }

        @Test
        @DisplayName("if the audit write itself fails (a changed_by that is not a real BARISTA), the status change is rolled back too and nothing is announced")
        void auditFailureRollsBackState() {
            Order order = place(customer("Alice"));
            long notificationsBefore = notifications.notificationsFor(order.id()).size();
            PrepareOrderCommand prepareWithBogusActor =
                    new PrepareOrderCommand(order.id(), orderService, events, preparations, Actor.SYSTEM, staffAccess) {
                        @Override public Long actorUserId() {
                            return 987_654L; // no such user: the history listener refuses to record it
                        }
                    };

            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> invoker.executeCommand(prepareWithBogusActor));
            assertTrue(failure.getMessage().contains("987654"), "the failure names the offending user id: " + failure.getMessage());

            assertEquals(OrderStatus.PLACED, orders.findById(order.id()).orElseThrow().status());
            assertEquals(1, trail(order.id()).size());
            assertEquals(notificationsBefore, notifications.notificationsFor(order.id()).size());
        }

        @Test
        @DisplayName("a supplied actor is recorded as changed_by (the Step 4 seam)")
        void actorIsMapped() {
            Order order = place(customer("Alice"));
            UserEntity barista = users.save(new UserEntity("Bob", Role.BARISTA));
            Instant at = Instant.now();

            tx.executeWithoutResult(status -> historyListener.onOrderStatusChanged(new OrderStatusChangedEvent(
                    order.id(), order.customerId(), OrderStatus.PLACED, OrderStatus.PREPARING, at, barista.id())));

            Long recordedActor = jdbc.queryForObject(
                    "SELECT changed_by FROM order_status_history WHERE order_id = ? AND to_status = 'PREPARING'",
                    Long.class, order.id());
            assertEquals(barista.id(), recordedActor);
        }

        @Test
        @DisplayName("only a BARISTA may be recorded: a MANAGER or CUSTOMER id is refused, and the message names the user id and role")
        void onlyBaristaMayBeRecorded() {
            Order order = place(customer("Alice"));
            UserEntity manager = manager("Maria");
            UserEntity other = customer("Mallory");

            for (UserEntity notABarista : List.of(manager, other)) {
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status ->
                        historyListener.onOrderStatusChanged(new OrderStatusChangedEvent(order.id(), order.customerId(),
                                OrderStatus.PLACED, OrderStatus.PREPARING, Instant.now(), notABarista.id()))));
                assertTrue(failure.getMessage().contains(String.valueOf(notABarista.id())), failure.getMessage());
                assertTrue(failure.getMessage().contains(notABarista.role().name()), failure.getMessage());
            }
            assertEquals(1, trail(order.id()).size(), "nothing was written");
        }

        @Test
        @DisplayName("a non-BARISTA actor smuggled into a command rolls the whole status change back (same transaction as the check)")
        void nonBaristaActorRollsBack() {
            Order order = place(customer("Alice"));
            UserEntity manager = manager("Maria");
            PrepareOrderCommand smuggled =
                    new PrepareOrderCommand(order.id(), orderService, events, preparations, Actor.SYSTEM, staffAccess) {
                        @Override public Long actorUserId() {
                            return manager.id();
                        }
                    };

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(smuggled));

            assertEquals(OrderStatus.PLACED, orders.findById(order.id()).orElseThrow().status());
            assertEquals(1, trail(order.id()).size());
        }

        @Test
        @DisplayName("the listener requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            Order order = place(customer("Alice"));
            assertThrows(IllegalTransactionStateException.class, () -> historyListener.onOrderStatusChanged(
                    new OrderStatusChangedEvent(order.id(), order.customerId(), null, OrderStatus.PLACED, Instant.now())));
        }

        @Test
        @DisplayName("rejects null collaborators")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> new OrderStatusHistoryListener(null, users, history));
            assertThrows(NullPointerException.class, () -> new OrderStatusHistoryListener(orders, null, history));
            assertThrows(NullPointerException.class, () -> new OrderStatusHistoryListener(orders, users, null));
        }
    }

    @Nested
    @DisplayName("AFTER_COMMIT listeners (notification and queue)")
    class AfterCommitTests {

        @Test
        @DisplayName("inside the transaction nothing is queued or announced and no other connection can see the order; after commit all three hold")
        void nothingBeforeCommitEverythingAfter() throws Exception {
            UserEntity customer = customer("Alice");
            Long[] placedId = new Long[1];

            tx.executeWithoutResult(status -> {
                placedId[0] = place(customer).id();
                try {
                    assertTrue(orderQueue.isEmpty(), "must not be queued before commit");
                    assertNull(notifications.latestFor(placedId[0]), "must not be announced before commit");
                    assertEquals(0, committedOrderCountSeenByAnotherThread(),
                            "a barista (another connection) cannot see the uncommitted order");
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });

            assertTrue(orderQueue.contains(placedId[0]));
            assertEquals("Order " + placedId[0] + " has been placed", notifications.latestFor(placedId[0]));
            assertEquals(1, committedOrderCountSeenByAnotherThread());
        }

        @Test
        @DisplayName("a rolled-back placement is never queued, announced, audited or persisted")
        void rollbackLeavesNoTrace() throws Exception {
            UserEntity customer = customer("Alice");
            Long[] placedId = new Long[1];

            tx.executeWithoutResult(status -> {
                placedId[0] = place(customer).id();
                status.setRollbackOnly();
            });

            assertTrue(orderQueue.isEmpty());
            assertNull(notifications.latestFor(placedId[0]));
            assertEquals(0, orders.count());
            assertEquals(0, history.count());
            assertEquals(0, committedOrderCountSeenByAnotherThread());
        }

        @Test
        @DisplayName("only PLACED is queued: a later transition adds nothing to the queue")
        void onlyPlacedIsQueued() throws InterruptedException {
            Order order = place(customer("Alice"));
            orderQueue.poll(0, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(orderQueue.isEmpty());

            facade.prepareOrder(order.id(), Actor.SYSTEM);

            assertTrue(orderQueue.isEmpty());
        }
    }

    @Nested
    @DisplayName("listener phase contract (guards against a regression that moves a listener to the wrong phase)")
    class PhaseContractTests {

        @Test
        @DisplayName("the queue dispatcher and the notification listener are AFTER_COMMIT transactional listeners")
        void afterCommit() throws Exception {
            for (Class<?> type : List.of(OrderQueueDispatcher.class, OrderNotificationListener.class)) {
                var method = type.getMethod("onOrderStatusChanged", OrderStatusChangedEvent.class);
                TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);
                assertNotNull(annotation, type.getSimpleName() + " must be a @TransactionalEventListener");
                assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
                assertFalse(annotation.fallbackExecution(), "a non-transactional publish must never be silently processed");
            }
        }

        @Test
        @DisplayName("the history listener is a plain in-transaction listener with MANDATORY propagation, not an AFTER_COMMIT one")
        void historyIsInTransaction() throws Exception {
            var method = OrderStatusHistoryListener.class.getMethod("onOrderStatusChanged", OrderStatusChangedEvent.class);
            assertNotNull(method.getAnnotation(EventListener.class));
            assertNull(method.getAnnotation(TransactionalEventListener.class));
            Transactional transactional = method.getAnnotation(Transactional.class);
            assertNotNull(transactional);
            assertEquals(Propagation.MANDATORY, transactional.propagation());
        }
    }

    @Test
    @DisplayName("sanity: the order used above really is an OrderEntity-backed row")
    void sanity() {
        Order order = place(customer("Alice"));
        OrderEntity entity = orders.findById(order.id()).orElseThrow();
        assertEquals(LoyaltyTier.REGULAR, entity.appliedLoyaltyTier());
    }
}
