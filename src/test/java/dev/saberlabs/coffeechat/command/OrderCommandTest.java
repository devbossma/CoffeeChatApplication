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
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import dev.saberlabs.coffeechat.entity.PaymentEntity;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.service.PaymentService;
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
    @Autowired PaymentService paymentService;

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

            assertThrows(UndoNotSupportedException.class, prepare(id)::undo);
            assertNull(invoker.undoLast(), "preparation is a barrier, so there is nothing to undo");

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

    /** Records a PAID payment for the order straight into the database (a fixture, not the behaviour under test). */
    private void markPaid(Long orderId) {
        Instant now = Instant.now();
        payments.saveAndFlush(new PaymentEntity(orders.getReferenceById(orderId), PaymentProvider.CASH,
                PRICE.total(), PaymentStatus.PAID, null, now, now));
    }

    private PayOrderCommand pay(Long orderId, PaymentProvider provider, PaymentGatewayResolver resolver) {
        return new PayOrderCommand(orderId, provider, resolver, orderService, paymentService);
    }

    private static PaymentGatewayResolver decliningPayPal() {
        return new PaymentGatewayResolver(List.of(
                new PayPalAdapter(new PayPalPaymentService(1L)), // $0.01 balance: every real charge declines
                new StripeAdapter(),
                new CashPaymentAdapter()));
    }

    @Nested
    @DisplayName("PayOrderCommand")
    class PayOrderCommandTests {

        @Test
        @DisplayName("execute() charges the order's persisted total, keeps the result and records ONE PAID payment row")
        void execute() {
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = pay(id, PaymentProvider.CASH, gateways);

            invoker.executeCommand(command);

            assertTrue(command.result().isPaid());
            assertEquals(PaymentProvider.CASH, command.result().provider());
            assertEquals(0, PRICE.total().compareTo(command.result().amount()));
            PaymentEntity row = payments.findByOrderId(id).orElseThrow();
            assertEquals(PaymentStatus.PAID, row.status());
            assertEquals(0, PRICE.total().compareTo(row.amount()), "amount is the order total, not a caller value");
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("a gateway decline is a FAILED result, not an exception, and the FAILED row is committed")
        void executeDeclined() {
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = pay(id, PaymentProvider.PAYPAL, decliningPayPal());

            invoker.executeCommand(command);

            assertFalse(command.result().isPaid());
            assertEquals(PaymentStatus.FAILED, payments.findByOrderId(id).orElseThrow().status());
            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("a FAILED payment can be retried: the same row becomes PAID (only the latest attempt is kept)")
        void retryAfterFailure() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(pay(id, PaymentProvider.PAYPAL, decliningPayPal()));
            PaymentEntity failed = payments.findByOrderId(id).orElseThrow();

            invoker.executeCommand(pay(id, PaymentProvider.CASH, gateways));

            PaymentEntity retried = payments.findByOrderId(id).orElseThrow();
            assertEquals(failed.id(), retried.id(), "same row, not a second one");
            assertEquals(PaymentStatus.PAID, retried.status());
            assertEquals(PaymentProvider.CASH, retried.provider());
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("paying an already PAID order is rejected cleanly and the gateway is never called")
        void alreadyPaidRejectedWithoutCharging() {
            Long id = orderAt(OrderStatus.READY);
            markPaid(id);
            java.util.concurrent.atomic.AtomicInteger charges = new java.util.concurrent.atomic.AtomicInteger();
            PaymentGatewayResolver counting = new PaymentGatewayResolver(List.of(
                    new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter() {
                        @Override
                        protected dev.saberlabs.coffeechat.adapter.PaymentResult doPay(String ref, BigDecimal amount) {
                            charges.incrementAndGet();
                            return super.doPay(ref, amount);
                        }
                    }));

            assertThrows(OrderStateConflictException.class,
                    () -> invoker.executeCommand(pay(id, PaymentProvider.CASH, counting)));

            assertEquals(0, charges.get(), "an already-paid order must never reach the gateway");
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("only a READY order is payable")
        void onlyReadyIsPayable() {
            for (OrderStatus status : List.of(OrderStatus.PLACED, OrderStatus.FULFILLED, OrderStatus.CANCELLED)) {
                Long id = orderAt(status);
                assertThrows(OrderStateConflictException.class,
                        () -> invoker.executeCommand(pay(id, PaymentProvider.CASH, gateways)), status.name());
            }
            assertEquals(0, payments.count());
        }

        @Test
        @DisplayName("an adapter that reports a different amount than the order total is rejected and nothing is persisted")
        void amountMustEqualTotal() {
            PaymentGatewayResolver wrongAmount = new PaymentGatewayResolver(List.of(
                    new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter() {
                        @Override
                        protected dev.saberlabs.coffeechat.adapter.PaymentResult doPay(String ref, BigDecimal amount) {
                            return new dev.saberlabs.coffeechat.adapter.PaymentResult(PaymentProvider.CASH, ref,
                                    amount.add(BigDecimal.ONE), PaymentStatus.PAID, "wrong");
                        }
                    }));
            Long id = orderAt(OrderStatus.READY);

            assertThrows(IllegalStateException.class,
                    () -> invoker.executeCommand(pay(id, PaymentProvider.CASH, wrongAmount)));

            assertEquals(0, payments.count());
        }

        @Test
        @DisplayName("does not change the order status")
        void doesNotChangeStatus() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(pay(id, PaymentProvider.CASH, gateways));
            assertEquals(OrderStatus.READY, statusOf(id));
        }

        @Test
        @DisplayName("undo() is not supported: refunds are not modelled")
        void undoNotSupported() {
            Long id = orderAt(OrderStatus.READY);
            PayOrderCommand command = pay(id, PaymentProvider.CASH, gateways);
            invoker.executeCommand(command);

            assertThrows(UndoNotSupportedException.class, command::undo);
            assertNull(invoker.undoLast(), "payment is a barrier, so there is nothing to undo");

            assertTrue(command.result().isPaid(), "the payment result is untouched");
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("execute() rejects an unknown order id")
        void unknownOrder() {
            PayOrderCommand command = pay(404L, PaymentProvider.CASH, gateways);
            assertThrows(OrderNotFoundException.class, () -> invoker.executeCommand(command));
        }

        @Test
        @DisplayName("constructor rejects null arguments")
        void rejectsNulls() {
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(null, PaymentProvider.CASH, gateways, orderService, paymentService));
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, null, gateways, orderService, paymentService));
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, PaymentProvider.CASH, null, orderService, paymentService));
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, PaymentProvider.CASH, gateways, null, paymentService));
            assertThrows(NullPointerException.class,
                    () -> new PayOrderCommand(1L, PaymentProvider.CASH, gateways, orderService, null));
        }

        @Test
        @DisplayName("is named PayOrder")
        void name() {
            assertEquals("PayOrder", pay(1L, PaymentProvider.CASH, gateways).name());
        }
    }

    @Nested
    @DisplayName("FulfillOrderCommand")
    class FulfillOrderCommandTests {

        private FulfillOrderCommand fulfill(Long orderId) {
            return new FulfillOrderCommand(orderId, orderService, events, users, paymentService);
        }

        @Test
        @DisplayName("execute() moves a PAID READY order to FULFILLED and bumps the customer's count by exactly one")
        void execute() {
            Long id = orderAt(OrderStatus.READY);
            markPaid(id);

            invoker.executeCommand(fulfill(id));

            assertEquals(OrderStatus.FULFILLED, statusOf(id));
            assertEquals(1, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("execute() on an UNPAID order is rejected (409) and neither the status nor the count changes")
        void unpaidRejected() {
            Long id = orderAt(OrderStatus.READY);

            assertThrows(OrderStateConflictException.class, () -> invoker.executeCommand(fulfill(id)));

            assertEquals(OrderStatus.READY, statusOf(id));
            assertEquals(0, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("execute() on an order whose payment FAILED is rejected too")
        void failedPaymentRejected() {
            Long id = orderAt(OrderStatus.READY);
            invoker.executeCommand(pay(id, PaymentProvider.PAYPAL, decliningPayPal()));

            assertThrows(OrderStateConflictException.class, () -> invoker.executeCommand(fulfill(id)));

            assertEquals(0, fulfilledOrdersOf(customer.id()));
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
            markPaid(id);
            invoker.executeCommand(fulfill(id));

            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(fulfill(id)));

            assertEquals(1, fulfilledOrdersOf(customer.id()));
        }

        @Test
        @DisplayName("undo() is not supported, so a fulfilment can never be re-done and counted twice")
        void undoNotSupported() {
            Long id = orderAt(OrderStatus.READY);
            markPaid(id);
            FulfillOrderCommand command = fulfill(id);
            invoker.executeCommand(command);

            assertThrows(UndoNotSupportedException.class, command::undo);
            assertNull(invoker.undoLast(), "fulfilment is a barrier, so there is nothing to undo");

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
        @DisplayName("constructor rejects a null UserRepository or PaymentService")
        void rejectsNulls() {
            assertThrows(NullPointerException.class,
                    () -> new FulfillOrderCommand(1L, orderService, events, null, paymentService));
            assertThrows(NullPointerException.class,
                    () -> new FulfillOrderCommand(1L, orderService, events, users, null));
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
