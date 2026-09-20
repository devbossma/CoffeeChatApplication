package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import dev.saberlabs.coffeechat.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role enforcement (CLAUDE.md resolved decision 3): only BARISTA/MANAGER (or the system) may make an
 * order status transition; who may pay is staff, the order's own customer, or the system. Every
 * rejection is proven to leave NO trace: status, audit rows, payments, fulfilled_orders,
 * notifications and the gateway are all untouched.
 */
@DisplayName("CoffeeShopFacade role enforcement")
class CoffeeShopFacadeRolesTest extends AbstractIntegrationTest {

    private final AtomicInteger charges = new AtomicInteger();
    private CoffeeShopFacade facade;
    private UserEntity alice;
    private UserEntity otherCustomer;
    private UserEntity barista;
    private UserEntity manager;

    @BeforeEach
    void seed() {
        charges.set(0);
        facade = facadeWith(TestGateways.countingCash(charges));
        alice = customer("Alice");
        otherCustomer = customer("Mallory");
        barista = barista("Bob");
        manager = manager("Maria");
    }

    private Order placed() {
        return facade.placeOrder(new PlaceOrderRequest(alice.id(), CoffeeType.ESPRESSO, List.of()));
    }

    private Long ready() {
        Order order = placed();
        facade.prepareOrder(order.id(), Actor.SYSTEM);
        return order.id();
    }

    private Long readyAndPaid() {
        Long id = ready();
        facade.payOrder(id, PaymentProvider.CASH, Actor.SYSTEM);
        return id;
    }

    /** Everything a rejected action must leave untouched. */
    private record Trace(OrderStatus status, long historyRows, long payments, long fulfilled, int notifications, int charges) { }

    private Trace trace(Long orderId) {
        return new Trace(facade.getOrder(orderId).status(), history.count(), payments.count(),
                fulfilledOrdersOf(alice.id()), notifications.notificationsFor(orderId).size(), charges.get());
    }

    private List<Map<String, Object>> auditRows(Long orderId) {
        return jdbc.queryForList("SELECT to_status, changed_by FROM order_status_history WHERE order_id = ? ORDER BY id", orderId);
    }

    @Nested
    @DisplayName("prepareOrder()")
    class PrepareOrderTests {

        @Test
        @DisplayName("a BARISTA, a MANAGER and the SYSTEM may prepare")
        void allowed() {
            for (Actor actor : List.of(Actor.user(barista.id()), Actor.user(manager.id()), Actor.SYSTEM)) {
                Long id = placed().id();
                facade.prepareOrder(id, actor);
                assertEquals(OrderStatus.READY, facade.getOrder(id).status(), String.valueOf(actor));
            }
        }

        @Test
        @DisplayName("a CUSTOMER (even the order's own) is rejected with 403 and nothing changes")
        void customerRejected() {
            Long id = placed().id();
            Trace before = trace(id);

            RoleNotAllowedException e = assertThrows(RoleNotAllowedException.class,
                    () -> facade.prepareOrder(id, Actor.user(alice.id())));

            assertEquals(alice.id(), e.userId());
            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("an unknown user id is rejected with 401 (UnknownActorException) and nothing changes")
        void unknownRejected() {
            Long id = placed().id();
            Trace before = trace(id);

            assertThrows(UnknownActorException.class, () -> facade.prepareOrder(id, Actor.user(987_654L)));

            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("changed_by: a BARISTA is recorded on both hops, a MANAGER and the SYSTEM are NULL")
        void changedByRule() {
            Long byBarista = placed().id();
            Long byManager = placed().id();
            Long bySystem = placed().id();

            facade.prepareOrder(byBarista, Actor.user(barista.id()));
            facade.prepareOrder(byManager, Actor.user(manager.id()));
            facade.prepareOrder(bySystem, Actor.SYSTEM);

            List<Map<String, Object>> baristaRows = auditRows(byBarista);
            assertNull(baristaRows.get(0).get("changed_by"), "the placement row is a system row");
            assertEquals(barista.id(), baristaRows.get(1).get("changed_by"));
            assertEquals(barista.id(), baristaRows.get(2).get("changed_by"));
            assertTrue(auditRows(byManager).stream().allMatch(r -> r.get("changed_by") == null));
            assertTrue(auditRows(bySystem).stream().allMatch(r -> r.get("changed_by") == null));
        }
    }

    @Nested
    @DisplayName("fulfillOrder()")
    class FulfillOrderTests {

        @Test
        @DisplayName("a BARISTA, a MANAGER and the SYSTEM may fulfil a paid order, each counted once")
        void allowed() {
            for (Actor actor : List.of(Actor.user(barista.id()), Actor.user(manager.id()), Actor.SYSTEM)) {
                Long id = readyAndPaid();
                facade.fulfillOrder(id, actor);
                assertEquals(OrderStatus.FULFILLED, facade.getOrder(id).status());
            }
            assertEquals(3, fulfilledOrdersOf(alice.id()));
        }

        @Test
        @DisplayName("a CUSTOMER (even the order's own) is rejected with 403 and status, audit and the loyalty count are untouched")
        void customerRejected() {
            Long id = readyAndPaid();
            Trace before = trace(id);

            assertThrows(RoleNotAllowedException.class, () -> facade.fulfillOrder(id, Actor.user(alice.id())));

            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("an unknown user id is rejected with 401 and nothing changes")
        void unknownRejected() {
            Long id = readyAndPaid();
            Trace before = trace(id);

            assertThrows(UnknownActorException.class, () -> facade.fulfillOrder(id, Actor.user(987_654L)));

            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("changed_by on the FULFILLED row: BARISTA recorded, MANAGER NULL")
        void changedByRule() {
            Long byBarista = readyAndPaid();
            Long byManager = readyAndPaid();

            facade.fulfillOrder(byBarista, Actor.user(barista.id()));
            facade.fulfillOrder(byManager, Actor.user(manager.id()));

            List<Map<String, Object>> b = auditRows(byBarista);
            assertEquals(barista.id(), b.get(b.size() - 1).get("changed_by"));
            List<Map<String, Object>> m = auditRows(byManager);
            assertNull(m.get(m.size() - 1).get("changed_by"));
        }
    }

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrderTests {

        @Test
        @DisplayName("a BARISTA, a MANAGER and the SYSTEM may cancel")
        void allowed() {
            for (Actor actor : List.of(Actor.user(barista.id()), Actor.user(manager.id()), Actor.SYSTEM)) {
                Long id = placed().id();
                facade.cancelOrder(id, actor);
                assertEquals(OrderStatus.CANCELLED, facade.getOrder(id).status());
            }
        }

        @Test
        @DisplayName("a CUSTOMER cannot cancel their own order through the API (known limitation): 403, nothing changes")
        void customerRejected() {
            Long id = placed().id();
            Trace before = trace(id);

            assertThrows(RoleNotAllowedException.class, () -> facade.cancelOrder(id, Actor.user(alice.id())));

            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("an unknown user id is rejected with 401 and nothing changes")
        void unknownRejected() {
            Long id = placed().id();
            Trace before = trace(id);

            assertThrows(UnknownActorException.class, () -> facade.cancelOrder(id, Actor.user(987_654L)));

            assertEquals(before, trace(id));
        }
    }

    @Nested
    @DisplayName("payOrder()")
    class PayOrderTests {

        @Test
        @DisplayName("staff (BARISTA, MANAGER), the order's own CUSTOMER and the SYSTEM may pay")
        void allowed() {
            for (Actor actor : List.of(Actor.user(barista.id()), Actor.user(manager.id()),
                    Actor.user(alice.id()), Actor.SYSTEM)) {
                Long id = ready();
                assertTrue(facade.payOrder(id, PaymentProvider.CASH, actor).isPaid(), String.valueOf(actor));
            }
            assertEquals(4, payments.count());
        }

        @Test
        @DisplayName("a DIFFERENT customer paying someone else's order is rejected with 403, before the gateway: no charge, no payment row, nothing changes")
        void otherCustomerRejected() {
            Long id = ready();
            Trace before = trace(id);

            RoleNotAllowedException e = assertThrows(RoleNotAllowedException.class,
                    () -> facade.payOrder(id, PaymentProvider.CASH, Actor.user(otherCustomer.id())));

            assertEquals(otherCustomer.id(), e.userId());
            assertEquals(0, charges.get(), "the gateway was never called");
            assertEquals(before, trace(id));
        }

        @Test
        @DisplayName("an unknown user id is rejected with 401 before the gateway: no charge, nothing changes")
        void unknownRejected() {
            Long id = ready();
            Trace before = trace(id);

            assertThrows(UnknownActorException.class,
                    () -> facade.payOrder(id, PaymentProvider.CASH, Actor.user(987_654L)));

            assertEquals(0, charges.get());
            assertEquals(before, trace(id));
        }
    }

    @Nested
    @DisplayName("undoLastAction()")
    class UndoLastActionTests {

        @Test
        @DisplayName("staff and the SYSTEM may undo; a CUSTOMER gets 403 and the placement is not undone; an unknown user gets 401")
        void roles() {
            Long id = placed().id();
            Trace before = trace(id);

            assertThrows(RoleNotAllowedException.class, () -> facade.undoLastAction(Actor.user(alice.id())));
            assertThrows(UnknownActorException.class, () -> facade.undoLastAction(Actor.user(987_654L)));
            assertEquals(before, trace(id));

            facade.undoLastAction(Actor.user(manager.id()));
            assertEquals(OrderStatus.CANCELLED, facade.getOrder(id).status());
        }

        @Test
        @DisplayName("the status change an undo makes is attributed to the user who asked for it, not to whoever ran the original command")
        void undoIsAttributedToTheUndoer() {
            Long id = placed().id();

            facade.undoLastAction(Actor.user(barista.id()));

            List<Map<String, Object>> rows = auditRows(id);
            assertEquals("CANCELLED", rows.get(rows.size() - 1).get("to_status"));
            assertEquals(barista.id(), rows.get(rows.size() - 1).get("changed_by"));
            assertNull(rows.get(0).get("changed_by"), "the placement itself stays a system row");
        }
    }

    @Nested
    @DisplayName("placeOrder / getOrder / reorder with an actor")
    class OwnerOrStaffTests {

        @Test
        @DisplayName("a customer may place, read and reorder their own order; staff may do so for anyone")
        void allowed() {
            Order mine = facade.placeOrder(new PlaceOrderRequest(alice.id(), CoffeeType.ESPRESSO, List.of()), Actor.user(alice.id()));
            assertEquals(mine.id(), facade.getOrder(mine.id(), Actor.user(alice.id())).id());
            assertEquals(mine.id(), facade.getOrder(mine.id(), Actor.user(barista.id())).id());
            assertEquals(mine.id(), facade.getOrder(mine.id(), Actor.user(manager.id())).id());
            assertEquals(alice.id(), facade.reorder(mine.id(), Actor.user(alice.id())).customerId());
            assertEquals(alice.id(), facade.reorder(mine.id(), Actor.user(barista.id())).customerId());
            assertEquals(alice.id(), facade.placeOrder(new PlaceOrderRequest(alice.id(), CoffeeType.LATTE, List.of()), Actor.user(manager.id())).customerId());
        }

        @Test
        @DisplayName("another customer is refused (403) for each, and nothing is placed")
        void otherCustomer() {
            Long id = placed().id();
            long ordersBefore = orders.count();
            Actor mallory = Actor.user(otherCustomer.id());

            assertThrows(RoleNotAllowedException.class, () -> facade.getOrder(id, mallory));
            assertThrows(RoleNotAllowedException.class, () -> facade.reorder(id, mallory));
            assertThrows(RoleNotAllowedException.class,
                    () -> facade.placeOrder(new PlaceOrderRequest(alice.id(), CoffeeType.ESPRESSO, List.of()), mallory));
            assertEquals(ordersBefore, orders.count());
        }

        @Test
        @DisplayName("an unknown user is 401 even for an order that does not exist; a known user gets 404 for a missing order")
        void unknownAndMissing() {
            assertThrows(UnknownActorException.class, () -> facade.getOrder(987_654L, Actor.user(987_654L)));
            assertThrows(OrderNotFoundException.class, () -> facade.getOrder(987_654L, Actor.user(alice.id())));
            assertThrows(UnknownActorException.class,
                    () -> facade.placeOrder(new PlaceOrderRequest(alice.id(), CoffeeType.ESPRESSO, List.of()), Actor.user(987_654L)));
        }

        @Test
        @DisplayName("staff placing for a user who is not a customer is a not-found, not a success")
        void staffOrderingForNonCustomer() {
            assertThrows(CustomerNotFoundException.class,
                    () -> facade.placeOrder(new PlaceOrderRequest(barista.id(), CoffeeType.ESPRESSO, List.of()), Actor.user(manager.id())));
        }
    }

    @Nested
    @DisplayName("illegal transitions")
    class IllegalTransitionTests {

        @Test
        @DisplayName("preparing a READY order and cancelling a FULFILLED one throw IllegalOrderTransitionException and change nothing")
        void illegal() {
            Long readyId = ready();
            Trace beforeReady = trace(readyId);
            assertThrows(dev.saberlabs.coffeechat.model.IllegalOrderTransitionException.class,
                    () -> facade.prepareOrder(readyId, Actor.user(barista.id())));
            assertEquals(beforeReady, trace(readyId));

            Long fulfilledId = readyAndPaid();
            facade.fulfillOrder(fulfilledId, Actor.user(barista.id()));
            Trace beforeCancel = trace(fulfilledId);
            assertThrows(dev.saberlabs.coffeechat.model.IllegalOrderTransitionException.class,
                    () -> facade.cancelOrder(fulfilledId, Actor.user(barista.id())));
            assertEquals(beforeCancel, trace(fulfilledId));
        }
    }

    @Nested
    @DisplayName("openShop() / closeShop()")
    class ShopTests {

        @Test
        @DisplayName("only a MANAGER may close and open the shop")
        void managerOnly() {
            facade.closeShop(Actor.user(manager.id()));
            assertFalse(coffeeShop.isOpen());
            facade.openShop(Actor.user(manager.id()));
            assertTrue(coffeeShop.isOpen());
        }

        @Test
        @DisplayName("a customer or barista gets 403, an unknown user 401, and the shop is untouched")
        void others() {
            for (Long id : List.of(alice.id(), barista.id())) {
                assertThrows(RoleNotAllowedException.class, () -> facade.closeShop(Actor.user(id)));
            }
            assertThrows(UnknownActorException.class, () -> facade.closeShop(Actor.user(987_654L)));
            assertTrue(coffeeShop.isOpen());

            coffeeShop.close();
            assertThrows(RoleNotAllowedException.class, () -> facade.openShop(Actor.user(barista.id())));
            assertFalse(coffeeShop.isOpen());
        }
    }

    @Nested
    @DisplayName("processOrder()")
    class ProcessOrderTests {

        @Test
        @DisplayName("as a BARISTA it prepares, pays and fulfils, recording the barista on each transition")
        void asBarista() {
            Long id = placed().id();

            OrderOutcome outcome = facade.processOrder(id, PaymentProvider.CASH, Actor.user(barista.id()));

            assertEquals(OrderStatus.FULFILLED, outcome.order().status());
            assertTrue(auditRows(id).stream().skip(1).allMatch(r -> barista.id().equals(r.get("changed_by"))));
        }

        @Test
        @DisplayName("as a CUSTOMER it is rejected at the first (prepare) step with 403 and nothing changes")
        void asCustomerRejected() {
            Long id = placed().id();
            Trace before = trace(id);

            assertThrows(RoleNotAllowedException.class,
                    () -> facade.processOrder(id, PaymentProvider.CASH, Actor.user(alice.id())));

            assertEquals(before, trace(id));
        }
    }

    @Test
    @DisplayName("Actor.SYSTEM is not a user: it can be told apart from a real actor")
    void systemIsNotAUser() {
        assertTrue(Actor.SYSTEM.isSystem());
        assertTrue(!Actor.user(1L).isSystem());
    }
}
