package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.adapter.PaymentGateway;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.PaymentEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PaymentService")
class PaymentServiceTest extends AbstractIntegrationTest {

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("3.50"), new BigDecimal("0.50"), new BigDecimal("0.00"));

    @Autowired PaymentService service;
    @Autowired TransactionTemplate tx;

    private OrderEntity readyOrder() {
        UserEntity customer = customer("Alice");
        Instant now = Instant.now();
        return orders.saveAndFlush(new OrderEntity(customer, CoffeeType.LATTE, List.of(), OrderStatus.READY,
                LoyaltyTier.REGULAR, PRICE, now, now));
    }

    /** A gateway that reports whatever status/amount it is told to, and counts calls. */
    private static final class ScriptedGateway implements PaymentGateway {
        final AtomicInteger calls = new AtomicInteger();
        private final PaymentStatus status;
        private final BigDecimal reportedAmount;

        ScriptedGateway(PaymentStatus status, BigDecimal reportedAmount) {
            this.status = status;
            this.reportedAmount = reportedAmount;
        }

        @Override public PaymentProvider provider() { return PaymentProvider.CASH; }

        @Override
        public PaymentResult pay(String orderRef, BigDecimal amount) {
            calls.incrementAndGet();
            return new PaymentResult(PaymentProvider.CASH, orderRef,
                    reportedAmount != null ? reportedAmount : amount, status, "scripted");
        }
    }

    private PaymentResult charge(Long orderId, PaymentProvider provider, PaymentGateway gateway) {
        return tx.execute(s -> service.charge(orders.findById(orderId).orElseThrow(), provider, gateway));
    }

    @Nested
    @DisplayName("charge()")
    class ChargeTests {

        @Test
        @DisplayName("records a PAID payment for exactly the order's total")
        void recordsPaid() {
            OrderEntity order = readyOrder();
            ScriptedGateway gateway = new ScriptedGateway(PaymentStatus.PAID, null);

            PaymentResult result = charge(order.id(), PaymentProvider.CASH, gateway);

            assertTrue(result.isPaid());
            PaymentEntity row = payments.findByOrderId(order.id()).orElseThrow();
            assertEquals(PaymentStatus.PAID, row.status());
            assertEquals(0, PRICE.total().compareTo(row.amount()));
            assertEquals(PaymentProvider.CASH, row.provider());
        }

        @Test
        @DisplayName("records a FAILED payment and returns the FAILED result instead of throwing")
        void recordsFailed() {
            OrderEntity order = readyOrder();

            PaymentResult result = charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.FAILED, null));

            assertFalse(result.isPaid());
            assertEquals(PaymentStatus.FAILED, payments.findByOrderId(order.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("a retry after FAILED updates the same row and only the latest attempt is kept")
        void retryUpdatesSameRow() {
            OrderEntity order = readyOrder();
            charge(order.id(), PaymentProvider.PAYPAL, new ScriptedGateway(PaymentStatus.FAILED, null));
            PaymentEntity failed = payments.findByOrderId(order.id()).orElseThrow();

            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.PAID, null));

            PaymentEntity retried = payments.findByOrderId(order.id()).orElseThrow();
            assertEquals(failed.id(), retried.id());
            assertEquals(PaymentStatus.PAID, retried.status());
            assertEquals(PaymentProvider.CASH, retried.provider());
            assertEquals("scripted", retried.detail());
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("a second FAILED attempt keeps the row FAILED and refreshes updatedAt")
        void secondFailure() {
            OrderEntity order = readyOrder();
            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.FAILED, null));
            Instant first = payments.findByOrderId(order.id()).orElseThrow().updatedAt();

            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.FAILED, null));

            PaymentEntity row = payments.findByOrderId(order.id()).orElseThrow();
            assertEquals(PaymentStatus.FAILED, row.status());
            assertTrue(!row.updatedAt().isBefore(first));
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("a PAID order is rejected BEFORE the gateway is called")
        void paidRejectedWithoutCharging() {
            OrderEntity order = readyOrder();
            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.PAID, null));
            ScriptedGateway second = new ScriptedGateway(PaymentStatus.PAID, null);

            assertThrows(OrderStateConflictException.class, () -> charge(order.id(), PaymentProvider.CASH, second));

            assertEquals(0, second.calls.get());
            assertEquals(1, payments.count());
        }

        @Test
        @DisplayName("a gateway reporting a different amount than the order total is rejected and nothing is persisted")
        void amountMismatch() {
            OrderEntity order = readyOrder();
            ScriptedGateway wrong = new ScriptedGateway(PaymentStatus.PAID, new BigDecimal("1.00"));

            assertThrows(IllegalStateException.class, () -> charge(order.id(), PaymentProvider.CASH, wrong));

            assertEquals(0, payments.count());
        }

        @Test
        @DisplayName("requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            OrderEntity order = readyOrder();
            assertThrows(IllegalTransactionStateException.class, () -> service.charge(
                    order, PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.PAID, null)));
        }

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNulls() {
            OrderEntity order = readyOrder();
            ScriptedGateway gateway = new ScriptedGateway(PaymentStatus.PAID, null);
            assertThrows(NullPointerException.class,
                    () -> tx.execute(s -> service.charge(null, PaymentProvider.CASH, gateway)));
            assertThrows(NullPointerException.class,
                    () -> tx.execute(s -> service.charge(order, null, gateway)));
            assertThrows(NullPointerException.class,
                    () -> tx.execute(s -> service.charge(order, PaymentProvider.CASH, null)));
        }
    }

    @Nested
    @DisplayName("requirePaid()")
    class RequirePaidTests {

        @Test
        @DisplayName("returns the PAID payment")
        void returnsPaid() {
            OrderEntity order = readyOrder();
            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.PAID, null));

            PaymentEntity paid = tx.execute(s -> service.requirePaid(order.id()));

            assertEquals(PaymentStatus.PAID, paid.status());
        }

        @Test
        @DisplayName("rejects an order with no payment")
        void noPayment() {
            OrderEntity order = readyOrder();
            assertThrows(OrderStateConflictException.class, () -> tx.execute(s -> service.requirePaid(order.id())));
        }

        @Test
        @DisplayName("rejects an order whose payment FAILED")
        void failedPayment() {
            OrderEntity order = readyOrder();
            charge(order.id(), PaymentProvider.CASH, new ScriptedGateway(PaymentStatus.FAILED, null));
            assertThrows(OrderStateConflictException.class, () -> tx.execute(s -> service.requirePaid(order.id())));
        }

        @Test
        @DisplayName("requires an existing transaction (MANDATORY) and a non-null id")
        void contract() {
            assertThrows(IllegalTransactionStateException.class, () -> service.requirePaid(1L));
            assertThrows(NullPointerException.class, () -> tx.execute(s -> service.requirePaid(null)));
        }
    }

    @Test
    @DisplayName("constructor rejects a null repository")
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new PaymentService(null));
    }
}
