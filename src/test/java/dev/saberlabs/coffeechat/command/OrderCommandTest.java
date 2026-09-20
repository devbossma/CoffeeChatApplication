package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.CashPaymentAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalPaymentService;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.StripeAdapter;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle commands against the real database: each command is run through
 * {@link OrderInvoker} (so in exactly one transaction) and its effect is read back from the
 * repositories, not from the object the command was holding.
 */
@DisplayName("Order lifecycle commands")
class OrderCommandTest extends AbstractIntegrationTest {

    @Autowired OrderInvoker invoker;
    @Autowired OrderService orderService;
    @Autowired CustomerService customerService;
    @Autowired OrderEventPublisher events;
    @Autowired CoffeePreparationResolver preparations;
    @Autowired PaymentGatewayResolver gateways;

    private UserEntity customer;

    @BeforeEach
    void seedCustomer() {
        customer = customer("Alice");
    }

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));

    /** An order seeded straight into the database at {@code status}, bypassing the commands under test. */
    private Long orderAt(OrderStatus status) {
        Instant now = Instant.now();
        return orders.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(), status,
                LoyaltyTier.REGULAR, PRICE, now, now)).id();
    }

    private OrderStatus statusOf(Long orderId) {
        return orders.findById(orderId).orElseThrow().status();
    }

    @Nested
    @DisplayName("PlaceOrderCommand")
    class PlaceOrderCommandTests {

        private PlaceOrderCommand place(Long customerId) {
            return new PlaceOrderCommand(customerId, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, PRICE,
                    customerService, orderService, events);
        }

        @Test
        @DisplayName("execute() persists a PLACED order and, once committed, queues its id and notifies")
        void execute() {
            PlaceOrderCommand command = place(customer.id());

            invoker.executeCommand(command);

            assertEquals(OrderStatus.PLACED, statusOf(command.orderId()));
            assertTrue(orderQueue.contains(command.orderId()));
            assertEquals("Order " + command.orderId() + " has been placed", notifications.latestFor(command.orderId()));
        }

        @Test
        @DisplayName("undo() cancels the order")
        void undo() {
            PlaceOrderCommand command = place(customer.id());
            invoker.executeCommand(command);

            invoker.undoLast();

            assertEquals(OrderStatus.CANCELLED, statusOf(command.orderId()));
        }

        @Test
        @DisplayName("undo() is rejected once the order has moved past PLACED (it would have effects outside the order)")
        void undoRejectedAfterPreparation() {
            PlaceOrderCommand command = place(customer.id());
            invoker.executeCommand(command);
            jdbc.update("UPDATE orders SET status = 'READY' WHERE id = ?", command.orderId());

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast());

            assertEquals(OrderStatus.READY, statusOf(command.orderId()));
        }

        @Test
        @DisplayName("execute() rejects an unknown customer and persists nothing")
        void unknownCustomer() {
            assertThrows(CustomerNotFoundException.class, () -> invoker.executeCommand(place(9_999L)));
            assertEquals(0, orders.count());
            assertTrue(orderQueue.isEmpty());
        }

        @Test
        @DisplayName("execute() rejects a user who is not a CUSTOMER")
        void nonCustomerRejected() {
            UserEntity barista = users.save(new UserEntity("Bob", Role.BARISTA));
            assertThrows(CustomerNotFoundException.class, () -> invoker.executeCommand(place(barista.id())));
            assertEquals(0, orders.count());
        }

        @Test
        @DisplayName("is named PlaceOrder")
        void name() {
            assertEquals("PlaceOrder", place(customer.id()).name());
        }

        @Test
        @DisplayName("constructor rejects a null OrderService (AbstractOrderCommand)")
        void rejectsNullOrderService() {
            assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(
                    1L, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, PRICE, customerService, null, events));
        }

        @Test
        @DisplayName("constructor rejects a null OrderEventPublisher (AbstractOrderCommand)")
        void rejectsNullEvents() {
            assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(
                    1L, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, PRICE, customerService, orderService, null));
        }

        @Test
        @DisplayName("constructor rejects a null customer id, price and CustomerService")
        void rejectsOtherNulls() {
            assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(
                    null, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, PRICE, customerService, orderService, events));
            assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(
                    1L, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, null, customerService, orderService, events));
            assertThrows(NullPointerException.class, () -> new PlaceOrderCommand(
                    1L, CoffeeType.ESPRESSO, List.of(), LoyaltyTier.REGULAR, PRICE, null, orderService, events));
        }
    }

    @Nested
    @DisplayName("PrepareOrderCommand")
    class PrepareOrderCommandTests {

        private PrepareOrderCommand prepare(Long orderId) {
            return new PrepareOrderCommand(orderId, orderService, events, preparations);
        }

        @Test
        @DisplayName("execute() runs the Template Method recipe and moves PLACED -> READY")
        void execute() {
            Long id = orderAt(OrderStatus.PLACED);
            PrepareOrderCommand command = prepare(id);

            invoker.executeCommand(command);

            assertEquals(OrderStatus.READY, statusOf(id));
            assertFalse(command.preparationLog().isEmpty());
            assertEquals("Order " + id + " is ready for pickup", notifications.latestFor(id));
        }

        @Test
        @DisplayName("execute() finishes an order already at PREPARING (skips the first hop)")
        void resumesFromPreparing() {
            Long id = orderAt(OrderStatus.PREPARING);

            invoker.executeCommand(prepare(id));

            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("execute() rejects an order that is no longer preparable and leaves it unchanged")
        void rejectsNotPreparable() {
            Long id = orderAt(OrderStatus.FULFILLED);

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(prepare(id)));

            assertEquals(OrderStatus.FULFILLED, statusOf(id));
        }

        @Test
        @DisplayName("execute() rejects an unknown order id")
        void unknownOrder() {
            assertThrows(OrderNotFoundException.class, () -> invoker.executeCommand(prepare(404L)));
        }

        @Test
        @DisplayName("undo() is not supported: a prepared order is never sent back to PLACED")
        void undoNotSupported() {
            Long id = orderAt(OrderStatus.PLACED);
            invoker.executeCommand(prepare(id));

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast());

            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("is named PrepareOrder")
        void name() {
            assertEquals("PrepareOrder", prepare(1L).name());
        }

        @Test
        @DisplayName("constructor rejects a null CoffeePreparationResolver")
        void rejectsNullResolver() {
            assertThrows(NullPointerException.class,
                    () -> new PrepareOrderCommand(1L, orderService, events, null));
        }
    }

    @Nested
    @DisplayName("PayOrderCommand")
    class PayOrderCommandTests {

        @Test
        @DisplayName("execute() charges the chosen gateway the order's persisted total and keeps the result")
        void execute() {
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(id, PaymentProvider.CASH, gateways, orderService);

            invoker.executeCommand(command);

            assertTrue(command.result().isPaid());
            assertEquals(PaymentProvider.CASH, command.result().provider());
            assertEquals(0, PRICE.total().compareTo(command.result().amount()));
        }

        @Test
        @DisplayName("execute() throws PaymentFailedException when the gateway declines")
        void executeDeclined() {
            PaymentGatewayResolver brokeGateways = new PaymentGatewayResolver(List.of(
                    new PayPalAdapter(new PayPalPaymentService(1L)), // $0.01 balance
                    new StripeAdapter(),
                    new CashPaymentAdapter()));
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(id, PaymentProvider.PAYPAL, brokeGateways, orderService);

            assertThrows(PaymentFailedException.class, () -> invoker.executeCommand(command));
        }

        @Test
        @DisplayName("does not change the order status")
        void doesNotChangeStatus() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(new PayOrderCommand(id, PaymentProvider.CASH, gateways, orderService));
            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("undo() is not supported: refunds are not modelled")
        void undoNotSupported() {
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(id, PaymentProvider.CASH, gateways, orderService);
            invoker.executeCommand(command);

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast());

            assertTrue(command.result().isPaid(), "the payment result is untouched");
        }

        @Test
        @DisplayName("execute() rejects an unknown order id")
        void unknownOrder() {
            PayOrderCommand command = new PayOrderCommand(404L, PaymentProvider.CASH, gateways, orderService);
            assertThrows(OrderNotFoundException.class, () -> invoker.executeCommand(command));
        }

        @Test
        @DisplayName("constructor rejects a null order id")
        void rejectsNullOrderId() {
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(null, PaymentProvider.CASH, gateways, orderService));
        }

        @Test
        @DisplayName("constructor rejects a null PaymentProvider")
        void rejectsNullProvider() {
            assertThrows(NullPointerException.class, () -> new PayOrderCommand(1L, null, gateways, orderService));
        }

        @Test
        @DisplayName("constructor rejects a null PaymentGatewayResolver")
        void rejectsNullGateways() {
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, PaymentProvider.CASH, null, orderService));
        }

        @Test
        @DisplayName("constructor rejects a null OrderService")
        void rejectsNullOrders() {
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, PaymentProvider.CASH, gateways, null));
        }
    }

    @Nested
    @DisplayName("FulfillOrderCommand")
    class FulfillOrderCommandTests {

        private FulfillOrderCommand fulfill(Long orderId) {
            return new FulfillOrderCommand(orderId, orderService, events, users);
        }

        @Test
        @DisplayName("execute() moves READY -> FULFILLED and bumps the customer's fulfilled count by exactly one")
        void execute() {
            Long id = orderAt(OrderStatus.READY);

            invoker.executeCommand(fulfill(id));

            assertEquals(OrderStatus.FULFILLED, statusOf(id));
            assertEquals(1, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("execute() on an order that is not READY is rejected and does not touch the count")
        void rejectsNotReady() {
            Long id = orderAt(OrderStatus.PLACED);

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(fulfill(id)));

            assertEquals(OrderStatus.PLACED, statusOf(id));
            assertEquals(0, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("a second fulfilment of the same order is rejected and the count stays at one")
        void doubleFulfilment() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(fulfill(id));

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(fulfill(id)));

            assertEquals(1, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("undo() is not supported, so a fulfilment can never be re-done and counted twice")
        void undoNotSupported() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(fulfill(id));

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast());

            assertEquals(OrderStatus.FULFILLED, statusOf(id));
            assertEquals(1, fulfilledOrdersOf(customer.id()));
            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(fulfill(id)));
            assertEquals(1, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("is named FulfillOrder")
        void name() {
            assertEquals("FulfillOrder", fulfill(1L).name());
        }

        @Test
        @DisplayName("constructor rejects a null UserRepository")
        void rejectsNullUsers() {
            assertThrows(NullPointerException.class,
                    () -> new FulfillOrderCommand(1L, orderService, events, null));
        }
    }

    @Nested
    @DisplayName("CancelOrderCommand")
    class CancelOrderCommandTests {

        @Test
        @DisplayName("execute() cancels an order that is still in progress")
        void execute() {
            Long id = orderAt(OrderStatus.PLACED);
            invoker.executeCommand(new CancelOrderCommand(id, orderService, events));
            assertEquals(OrderStatus.CANCELLED, statusOf(id));
        }

        @Test
        @DisplayName("execute() rejects cancelling an already-fulfilled order")
        void rejectsFulfilled() {
            Long id = orderAt(OrderStatus.FULFILLED);
            assertThrows(IllegalStateException.class,
                    () -> invoker.executeCommand(new CancelOrderCommand(id, orderService, events)));
            assertEquals(OrderStatus.FULFILLED, statusOf(id));
        }

        @Test
        @DisplayName("undo() of cancelling a PLACED order is not supported (it would never be queued again)")
        void undoNotSupportedFromPlaced() {
            Long id = orderAt(OrderStatus.PLACED);
            invoker.executeCommand(new CancelOrderCommand(id, orderService, events));

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast());

            assertEquals(OrderStatus.CANCELLED, statusOf(id));
        }

        @Test
        @DisplayName("undo() restores the status the order held before cancellation (READY)")
        void undo() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(new CancelOrderCommand(id, orderService, events));

            invoker.undoLast();

            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("is named CancelOrder")
        void name() {
            assertEquals("CancelOrder", new CancelOrderCommand(1L, orderService, events).name());
        }
    }
}
