package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.CashPaymentAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalAdapter;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.StripeAdapter;
import dev.saberlabs.coffeechat.command.OrderInvoker;
import dev.saberlabs.coffeechat.factory.CoffeeFactory;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import dev.saberlabs.coffeechat.strategy.PricingStrategyResolver;
import dev.saberlabs.coffeechat.strategy.RegularPricing;
import dev.saberlabs.coffeechat.strategy.SilverMemberPricing;
import dev.saberlabs.coffeechat.strategy.GoldMemberPricing;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("CoffeeShopFacade")
class CoffeeShopFacadeTest {

    private CoffeeShop coffeeShop;
    private OrderService orders;
    private CustomerService customers;
    private OrderInvoker invoker;
    private CoffeeShopFacade facade;

    @BeforeEach
    void setUp() {
        coffeeShop = new CoffeeShop(3);
        orders = new OrderService();
        customers = new CustomerService();
        invoker = new OrderInvoker();
        facade = new CoffeeShopFacade(
                coffeeShop,
                new CoffeeFactory(),
                new PricingStrategyResolver(List.of(
                        new RegularPricing(), new SilverMemberPricing(), new GoldMemberPricing())),
                new CoffeePreparationResolver(),
                new PaymentGatewayResolver(List.of(
                        new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter())),
                orders,
                customers,
                mock(OrderEventPublisher.class),
                invoker);
    }

    private Customer customerWithFulfilled(long fulfilled) {
        Customer customer = customers.create("Alice");
        for (long i = 0; i < fulfilled; i++) {
            customers.incrementFulfilled(customer.id());
        }
        return customer;
    }

    private PlaceOrderRequest request(long customerId, CoffeeType type, ExtraType... extras) {
        return new PlaceOrderRequest(customerId, type, List.of(extras));
    }

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrderTests {

        @Test
        @DisplayName("places a plain espresso for a REGULAR customer at full price")
        void plainEspresso() {
            Customer customer = customerWithFulfilled(0);
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
            Customer customer = customerWithFulfilled(0);
            Order order = facade.placeOrder(
                    request(customer.id(), CoffeeType.ESPRESSO, ExtraType.MILK, ExtraType.WHIPPED_CREAM));

            assertEquals(new BigDecimal("1.25"), order.price().extras()); // 0.50 + 0.75
            assertEquals(new BigDecimal("3.75"), order.price().total());
        }

        @Test
        @DisplayName("applies the SILVER 10% discount for a 6-order customer")
        void silverDiscount() {
            Customer customer = customerWithFulfilled(6);
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));

            assertEquals(LoyaltyTier.SILVER, order.appliedLoyaltyTier());
            assertEquals(new BigDecimal("0.40"), order.price().discount());
            assertEquals(new BigDecimal("3.60"), order.price().total());
        }

        @Test
        @DisplayName("applies the GOLD 20% discount for an 11-order customer")
        void goldDiscount() {
            Customer customer = customerWithFulfilled(11);
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));

            assertEquals(LoyaltyTier.GOLD, order.appliedLoyaltyTier());
            assertEquals(new BigDecimal("3.20"), order.price().total());
        }

        @Test
        @DisplayName("freezes the tier at placement — a later tier change does not re-price the order")
        void freezesTier() {
            Customer customer = customerWithFulfilled(5); // still REGULAR
            Order order = facade.placeOrder(request(customer.id(), CoffeeType.LATTE));
            customers.incrementFulfilled(customer.id()); // now SILVER

            assertEquals(LoyaltyTier.REGULAR, order.appliedLoyaltyTier());
            assertEquals(new BigDecimal("4.00"), order.price().total());
        }

        @Test
        @DisplayName("runs placement through the OrderInvoker")
        void goesThroughInvoker() {
            Customer customer = customerWithFulfilled(0);
            facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            assertEquals(List.of("PlaceOrder"), invoker.history());
        }

        @Test
        @DisplayName("rejects an order when the shop is closed")
        void rejectsWhenClosed() {
            Customer customer = customerWithFulfilled(0);
            coffeeShop.close();
            assertThrows(ShopClosedException.class,
                    () -> facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO)));
        }

        @Test
        @DisplayName("rejects an order for a coffee that is off the menu")
        void rejectsOffMenu() {
            Customer customer = customerWithFulfilled(0);
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
    }

    @Nested
    @DisplayName("getOrder()")
    class GetOrderTests {

        @Test
        @DisplayName("returns a placed order by id")
        void returnsOrder() {
            Customer customer = customerWithFulfilled(0);
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
    @DisplayName("processOrder()")
    class ProcessOrderTests {

        @Test
        @DisplayName("prepares, pays and fulfils the order end to end")
        void endToEnd() {
            Customer customer = customerWithFulfilled(0);
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));

            Order done = facade.processOrder(placed.id(), PaymentProvider.CASH);

            assertEquals(OrderStatus.FULFILLED, done.status());
            assertEquals(1, customers.findById(customer.id()).orElseThrow().fulfilledOrders());
            assertTrue(invoker.history().containsAll(List.of("PrepareOrder", "PayOrder", "FulfillOrder")));
        }
    }

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrderTests {

        @Test
        @DisplayName("cancels a placed order")
        void cancels() {
            Customer customer = customerWithFulfilled(0);
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.cancelOrder(placed.id());
            assertEquals(OrderStatus.CANCELLED, facade.getOrder(placed.id()).status());
        }
    }

    @Nested
    @DisplayName("undoLastAction()")
    class UndoLastActionTests {

        @Test
        @DisplayName("undoing a placement cancels the order")
        void undoPlacement() {
            Customer customer = customerWithFulfilled(0);
            Order placed = facade.placeOrder(request(customer.id(), CoffeeType.ESPRESSO));
            facade.undoLastAction();
            assertEquals(OrderStatus.CANCELLED, facade.getOrder(placed.id()).status());
        }

        @Test
        @DisplayName("is a no-op when nothing has been done")
        void noOpWhenEmpty() {
            facade.undoLastAction();
            assertEquals(0, invoker.pendingUndoCount());
        }
    }
}
