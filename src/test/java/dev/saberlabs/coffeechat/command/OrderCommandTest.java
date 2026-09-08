package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.CashPaymentAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalPaymentService;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.StripeAdapter;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Order lifecycle commands")
class OrderCommandTest {

    private OrderService orders;
    private CustomerService customers;
    private OrderEventPublisher events;
    private CoffeePreparationResolver preparations;
    private PaymentGatewayResolver gateways;

    @BeforeEach
    void setUp() {
        orders = new OrderService();
        customers = new CustomerService();
        events = mock(OrderEventPublisher.class);
        preparations = new CoffeePreparationResolver();
        gateways = new PaymentGatewayResolver(
                List.of(new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter()));
    }

    private Order savedUnplacedOrder() {
        Customer customer = customers.create("Alice");
        Order order = new Order(
                customer, new Espresso(), CoffeeType.ESPRESSO, List.of(),
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR);
        return orders.save(order);
    }

    private Order orderAt(OrderStatus status) {
        Order order = savedUnplacedOrder();
        order.transitionTo(OrderStatus.PLACED);
        if (status == OrderStatus.PLACED) {
            return order;
        }
        order.transitionTo(OrderStatus.PREPARING);
        if (status == OrderStatus.PREPARING) {
            return order;
        }
        order.transitionTo(OrderStatus.READY);
        return order;
    }

    @Nested
    @DisplayName("PlaceOrderCommand")
    class PlaceOrderCommandTests {

        @Test
        @DisplayName("execute() moves the order to PLACED, persists it and publishes null -> PLACED")
        void execute() {
            Order order = savedUnplacedOrder();
            new PlaceOrderCommand(order, orders, events).execute();

            assertEquals(OrderStatus.PLACED, order.status());
            assertTrue(orders.findById(order.id()).isPresent());
            verify(events).publishStatusChange(order, null);
        }

        @Test
        @DisplayName("undo() cancels the order")
        void undo() {
            Order order = savedUnplacedOrder();
            PlaceOrderCommand command = new PlaceOrderCommand(order, orders, events);
            command.execute();
            command.undo();
            assertEquals(OrderStatus.CANCELLED, order.status());
        }

        @Test
        @DisplayName("is named PlaceOrder")
        void name() {
            assertEquals("PlaceOrder", new PlaceOrderCommand(savedUnplacedOrder(), orders, events).name());
        }
    }

    @Nested
    @DisplayName("PrepareOrderCommand")
    class PrepareOrderCommandTests {

        @Test
        @DisplayName("execute() runs the Template Method recipe and moves PLACED -> READY")
        void execute() {
            Order order = orderAt(OrderStatus.PLACED);
            PrepareOrderCommand command = new PrepareOrderCommand(order, orders, events, preparations);

            command.execute();

            assertEquals(OrderStatus.READY, order.status());
            assertTrue(command.preparationLog().stream().anyMatch(s -> s.contains("is ready")));
            verify(events).publishStatusChange(order, OrderStatus.PLACED);
            verify(events).publishStatusChange(order, OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("undo() sends the order back to PLACED")
        void undo() {
            Order order = orderAt(OrderStatus.PLACED);
            PrepareOrderCommand command = new PrepareOrderCommand(order, orders, events, preparations);
            command.execute();
            command.undo();
            assertEquals(OrderStatus.PLACED, order.status());
        }

        @Test
        @DisplayName("is named PrepareOrder")
        void name() {
            assertEquals("PrepareOrder",
                    new PrepareOrderCommand(orderAt(OrderStatus.PLACED), orders, events, preparations).name());
        }
    }

    @Nested
    @DisplayName("PayOrderCommand")
    class PayOrderCommandTests {

        @Test
        @DisplayName("execute() charges the chosen gateway and keeps the result")
        void execute() {
            Order order = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(order, PaymentProvider.CASH, gateways);

            command.execute();

            assertTrue(command.result().isPaid());
            assertEquals(PaymentProvider.CASH, command.result().provider());
        }

        @Test
        @DisplayName("execute() throws PaymentFailedException when the gateway declines")
        void executeDeclined() {
            PaymentGatewayResolver brokeGateways = new PaymentGatewayResolver(List.of(
                    new PayPalAdapter(new PayPalPaymentService(1L)), // $0.01 balance
                    new StripeAdapter(),
                    new CashPaymentAdapter()));
            Order order = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(order, PaymentProvider.PAYPAL, brokeGateways);

            assertThrows(PaymentFailedException.class, command::execute);
        }

        @Test
        @DisplayName("does not change the order status")
        void doesNotChangeStatus() {
            Order order = orderAt(OrderStatus.READY);
            new PayOrderCommand(order, PaymentProvider.CASH, gateways).execute();
            assertEquals(OrderStatus.READY, order.status());
        }

        @Test
        @DisplayName("undo() clears the stored result")
        void undo() {
            Order order = orderAt(OrderStatus.READY);
            PayOrderCommand command = new PayOrderCommand(order, PaymentProvider.CASH, gateways);
            command.execute();
            command.undo();
            assertNull(command.result());
        }
    }

    @Nested
    @DisplayName("FulfillOrderCommand")
    class FulfillOrderCommandTests {

        @Test
        @DisplayName("execute() moves READY -> FULFILLED and bumps the customer's fulfilled count")
        void execute() {
            Order order = orderAt(OrderStatus.READY);
            long before = order.customer().fulfilledOrders();

            new FulfillOrderCommand(order, orders, events, customers).execute();

            assertEquals(OrderStatus.FULFILLED, order.status());
            assertEquals(before + 1, customers.findById(order.customer().id()).orElseThrow().fulfilledOrders());
            verify(events).publishStatusChange(order, OrderStatus.READY);
        }

        @Test
        @DisplayName("undo() reverts the status to READY (the count bump is not reversed)")
        void undo() {
            Order order = orderAt(OrderStatus.READY);
            FulfillOrderCommand command = new FulfillOrderCommand(order, orders, events, customers);
            command.execute();
            command.undo();
            assertEquals(OrderStatus.READY, order.status());
            assertEquals(1, customers.findById(order.customer().id()).orElseThrow().fulfilledOrders());
        }

        @Test
        @DisplayName("is named FulfillOrder")
        void name() {
            assertEquals("FulfillOrder",
                    new FulfillOrderCommand(orderAt(OrderStatus.READY), orders, events, customers).name());
        }
    }

    @Nested
    @DisplayName("CancelOrderCommand")
    class CancelOrderCommandTests {

        @Test
        @DisplayName("execute() cancels an order that is still in progress")
        void execute() {
            Order order = orderAt(OrderStatus.PREPARING);
            new CancelOrderCommand(order, orders, events).execute();
            assertEquals(OrderStatus.CANCELLED, order.status());
            verify(events).publishStatusChange(order, OrderStatus.PREPARING);
        }

        @Test
        @DisplayName("undo() restores the status the order held before cancellation")
        void undo() {
            Order order = orderAt(OrderStatus.PREPARING);
            CancelOrderCommand command = new CancelOrderCommand(order, orders, events);
            command.execute();
            command.undo();
            assertEquals(OrderStatus.PREPARING, order.status());
        }

        @Test
        @DisplayName("is named CancelOrder")
        void name() {
            assertEquals("CancelOrder",
                    new CancelOrderCommand(orderAt(OrderStatus.PLACED), orders, events).name());
        }
    }
}
