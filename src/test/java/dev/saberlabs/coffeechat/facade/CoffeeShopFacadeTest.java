package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.command.OrderInvoker;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facade against the real database and the real command pipeline (Strategy, Decorator, Factory,
 * Command, Template, Adapter all real; only the barista loops are stopped so orders stay put).
 */
@DisplayName("CoffeeShopFacade")
class CoffeeShopFacadeTest extends AbstractIntegrationTest {

    @Autowired CoffeeShopFacade facade;
    @Autowired OrderInvoker invoker;

    private PlaceOrderRequest request(long customerId, CoffeeType type, ExtraType... extras) {
        return new PlaceOrderRequest(customerId, type, List.of(extras));
    }

    private void bumpFulfilled(Long customerId) {
        jdbc.update("UPDATE user_accounts SET fulfilled_orders = fulfilled_orders + 1 WHERE id = ?", customerId);
    }

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrderTests {

        @Test
        @DisplayName("places a plain espresso for a REGULAR customer at full price")
        void plainEspresso() {
            UserEntity customer = customer("Alice");
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            assertNotNull(order.id());
            assertEquals(OrderStatus.PLACED, order.status());
            assertEquals(new BigDecimal("2.50"), order.price().total());
            assertEquals(LoyaltyTier.REGULAR, order.appliedLoyaltyTier());
            assertTrue(orders.findById(order.id()).isPresent());
        }

        @Test
        @DisplayName("prices in the extras via the Decorator")
        void withExtras() {
            UserEntity customer = customer("Alice");
            Order order = facade.placeOrder(
                    request(customer.id(), CoffeeType.ESPRESSO, ExtraType.MILK, ExtraType.WHIPPED_CREAM));

            assertEquals(new BigDecimal("1.25"), order.price().extras()); // 0.50 + 0.75
            assertEquals(new BigDecimal("3.75"), order.price().total());
        }

        @Test
        @DisplayName("applies the SILVER 10% discount for a 6-order customer")
        void silverDiscount() {
            UserEntity customer = customer("Alice", 6);
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));

            assertEquals(LoyaltyTier.SILVER, order.appliedLoyaltyTier());
            assertEquals(new BigDecimal("0.40"), order.price().discount());
            assertEquals(new BigDecimal("3.60"), order.price().total());
        }

        @Test
        @DisplayName("applies the GOLD 20% discount for an 11-order customer")
        void goldDiscount() {
            UserEntity customer = customer("Alice", 11);
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));

            assertEquals(LoyaltyTier.GOLD, order.appliedLoyaltyTier());
            assertEquals(new BigDecimal("3.20"), order.price().total());
        }

        @Test
        @DisplayName("freezes the tier at placement: a later tier change does not re-price or re-tier the stored order")
        void freezesTier() {
            UserEntity customer = customer("Alice", 5); // still REGULAR
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));
            bumpFulfilled(customer.id()); // now SILVER

            Order reloaded = facade.getOrder(order.id());
            assertEquals(LoyaltyTier.REGULAR, reloaded.appliedLoyaltyTier());
            assertEquals(new BigDecimal("4.00"), reloaded.price().total());
        }

        @Test
        @DisplayName("stores no tier: the customer's tier is derived from fulfilled_orders at read time")
        void tierIsDerived() {
            UserEntity customer = customer("Alice", 6);
            assertEquals(LoyaltyTier.SILVER, users.findById(customer.id()).orElseThrow().loyaltyTier());
        }

        @Test
        @DisplayName("runs placement through the OrderInvoker")
        void goesThroughInvoker() {
            UserEntity customer = customer("Alice");
            facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            List<String> history = invoker.history();
            assertEquals("PlaceOrder", history.get(history.size() - 1));
        }

        @Test
        @DisplayName("queues the placed order's id once it has committed")
        void queuesAfterCommit() {
            UserEntity customer = customer("Alice");
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            assertTrue(orderQueue.contains(order.id()));
        }

        @Test
        @DisplayName("rejects an order when the shop is closed")
        void rejectsWhenClosed() {
            UserEntity customer = customer("Alice");
            coffeeShop.close();
            assertThrows(ShopClosedException.class,
                    () -> facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO)));
            assertEquals(0, orders.count());
        }

        @Test
        @DisplayName("rejects an order for a coffee that is off the menu")
        void rejectsOffMenu() {
            UserEntity customer = customer("Alice");
            coffeeShop.stopServing(CoffeeType.LATTE);
            assertThrows(CoffeeNotOnMenuException.class,
                    () -> facade.placeOrder(request(customer.id(), CoffeeType.LATTE)));
        }

        @Test
        @DisplayName("rejects an order for an unknown customer")
        void rejectsUnknownCustomer() {
            assertThrows(CustomerNotFoundException.class,
                    () -> facade.placeOrder(request(999L, CoffeeType.ESPRESSO)));
        }

        @Test
        @DisplayName("rejects an order for a user who is not a CUSTOMER")
        void rejectsNonCustomer() {
            UserEntity barista = users.save(new UserEntity("Bob", Role.BARISTA));
            assertThrows(CustomerNotFoundException.class,
                    () -> facade.placeOrder(request(barista.id(), CoffeeType.ESPRESSO)));
            assertEquals(0, orders.count());
        }
    }

    @Nested
    @DisplayName("getOrder()")
    class GetOrderTests {

        @Test
        @DisplayName("returns a placed order by id")
        void returnsOrder() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            assertEquals(placed.id(), facade.getOrder(placed.id()).id());
        }

        @Test
        @DisplayName("throws for an unknown order id")
        void throwsForUnknown() {
            assertThrows(OrderNotFoundException.class, () -> facade.getOrder(404L));
        }
    }

    @Nested
    @DisplayName("prepareOrder()")
    class PrepareOrderTests {

        @Test
        @DisplayName("moves a placed order to READY")
        void prepares() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            facade.prepareOrder(placed.id());

            assertEquals(OrderStatus.READY, facade.getOrder(placed.id()).status());
        }

        @Test
        @DisplayName("throws for an unknown order id")
        void throwsForUnknown() {
            assertThrows(OrderNotFoundException.class, () -> facade.prepareOrder(404L));
        }
    }

    @Nested
    @DisplayName("processOrder()")
    class ProcessOrderTests {

        @Test
        @DisplayName("prepares, pays and fulfils the order end to end")
        void endToEnd() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            OrderOutcome outcome = facade.processOrder(placed.id(), PaymentProvider.CASH);

            assertEquals(OrderStatus.FULFILLED, outcome.order().status());
            assertTrue(outcome.payment().isPaid());
            assertTrue(outcome.fulfilled());
            assertEquals(1, fulfilledOrdersOf(customer.id()));
            assertEquals(1, payments.count());
            assertTrue(invoker.history().containsAll(List.of("PrepareOrder", "PayOrder", "FulfillOrder")));
        }
    }

    @Nested
    @DisplayName("processOrder() with a declining gateway")
    class ProcessOrderDeclinedTests {

        @Test
        @DisplayName("stops on a FAILED payment without fulfilling; the order stays READY and a retry then completes it")
        void stopsOnFailedPayment() {
            CoffeeShopFacade declining = facadeWith(dev.saberlabs.coffeechat.support.TestGateways.decliningPayPal());
            UserEntity customer = customer("Alice");
            Order placed = declining.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            OrderOutcome outcome = declining.processOrder(placed.id(), PaymentProvider.PAYPAL);

            assertTrue(!outcome.payment().isPaid());
            assertTrue(!outcome.fulfilled());
            assertEquals(OrderStatus.READY, outcome.order().status());
            assertEquals(0, fulfilledOrdersOf(customer.id()));
            assertEquals(1, payments.count());

            assertTrue(declining.payOrder(placed.id(), PaymentProvider.CASH).isPaid());
            declining.fulfillOrder(placed.id());

            assertEquals(OrderStatus.FULFILLED, declining.getOrder(placed.id()).status());
            assertEquals(1, fulfilledOrdersOf(customer.id()));
            assertEquals(1, payments.count(), "the retry reused the FAILED row");
        }
    }

    @Nested
    @DisplayName("payOrder() / fulfillOrder()")
    class PayAndFulfilTests {

        @Test
        @DisplayName("fulfilling an unpaid order is rejected (409 conflict) and earns the customer nothing")
        void unpaidFulfilRejected() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.prepareOrder(placed.id());

            assertThrows(OrderStateConflictException.class, () -> facade.fulfillOrder(placed.id()));

            assertEquals(OrderStatus.READY, facade.getOrder(placed.id()).status());
            assertEquals(0, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("a second payment of a PAID order is rejected")
        void doublePayRejected() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.prepareOrder(placed.id());
            facade.payOrder(placed.id(), PaymentProvider.CASH);

            assertThrows(OrderStateConflictException.class, () -> facade.payOrder(placed.id(), PaymentProvider.CASH));
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("paying an order that is not READY is rejected")
        void payBeforeReady() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            assertThrows(OrderStateConflictException.class, () -> facade.payOrder(placed.id(), PaymentProvider.CASH));
        }
    }

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrderTests {

        @Test
        @DisplayName("cancels a placed order")
        void cancels() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.cancelOrder(placed.id());
            assertEquals(OrderStatus.CANCELLED, facade.getOrder(placed.id()).status());
        }
    }

    @Nested
    @DisplayName("reorder()")
    class ReorderTests {

        @Test
        @DisplayName("re-places an equivalent order from the persisted extras: same coffee + ordered extras, new id, fresh PLACED status")
        void reordersEquivalent() {
            UserEntity customer = customer("Alice");
            Order original = facade.placeOrder(
                    request(customer.id(), CoffeeType.LATTE, ExtraType.MILK, ExtraType.SUGAR, ExtraType.MILK));
            facade.processOrder(original.id(), PaymentProvider.CASH);

            Order clone = facade.reorder(original.id());

            assertTrue(!clone.id().equals(original.id()));
            assertEquals(OrderStatus.PLACED, clone.status());
            assertEquals(CoffeeType.LATTE, clone.baseType());
            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR, ExtraType.MILK), clone.extras());
            assertEquals(original.coffeeDescription(), clone.coffeeDescription());
            assertEquals(OrderStatus.FULFILLED, facade.getOrder(original.id()).status(), "original untouched");
        }

        @Test
        @DisplayName("re-prices the clone against the customer's CURRENT tier, not the original's")
        void repricesAgainstCurrentTier() {
            UserEntity customer = customer("Alice", 5); // REGULAR
            Order original = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));
            assertEquals(LoyaltyTier.REGULAR, original.appliedLoyaltyTier());

            bumpFulfilled(customer.id()); // customer reaches SILVER before re-ordering
            Order clone = facade.reorder(original.id());

            assertEquals(LoyaltyTier.SILVER, clone.appliedLoyaltyTier());
            assertEquals(new BigDecimal("3.60"), clone.price().total());
        }

        @Test
        @DisplayName("throws for an unknown order id")
        void throwsForUnknown() {
            assertThrows(OrderNotFoundException.class, () -> facade.reorder(404L));
        }
    }

    @Nested
    @DisplayName("undoLastAction()")
    class UndoLastActionTests {

        @Test
        @DisplayName("undoing a placement cancels the order")
        void undoPlacement() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.undoLastAction();
            assertEquals(OrderStatus.CANCELLED, facade.getOrder(placed.id()).status());
        }
    }
}
