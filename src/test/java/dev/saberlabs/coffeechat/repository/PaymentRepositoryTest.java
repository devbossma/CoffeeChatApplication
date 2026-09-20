package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.PaymentEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PaymentRepository")
class PaymentRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity customer = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
        Instant now = Instant.now();
        PriceBreakdown price = PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        order = orderRepository.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(),
                OrderStatus.PLACED, LoyaltyTier.REGULAR, price, now, now));
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves and reloads a payment")
        void savesAndReloads() {
            Instant now = Instant.now();
            PaymentEntity saved = paymentRepository.saveAndFlush(new PaymentEntity(
                    order, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID,
                    "change $0.00", now, now));
            entityManager.clear();

            PaymentEntity reloaded = paymentRepository.findById(saved.id()).orElseThrow();
            assertEquals(PaymentProvider.CASH, reloaded.provider());
            assertEquals(PaymentStatus.PAID, reloaded.status());
        }

        @Test
        @DisplayName("rejects a second payment for the same order (unique(order_id))")
        void rejectsSecondPaymentForSameOrder() {
            Instant now = Instant.now();
            paymentRepository.saveAndFlush(new PaymentEntity(
                    order, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID, null, now, now));

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> paymentRepository.saveAndFlush(new PaymentEntity(
                            order, PaymentProvider.STRIPE, new BigDecimal("2.50"), PaymentStatus.PAID, null, now, now)));
            assertTrue(thrown.getMostSpecificCause().getMessage().contains("uq_payments_order_id"));
        }

        @Test
        @DisplayName("rejects a non-positive amount at the database level")
        void rejectsNonPositiveAmount() {
            // A fresh customer+order is inserted here (not the @BeforeEach fixture): the
            // @BeforeEach fixture lives in this test's own JPA transaction, uncommitted, so it is
            // invisible to assertConstraintViolation's separate connection -- referencing it here
            // would fail on the order_id FK instead of the amount CHECK this test names. Setup and
            // violating statement run on the SAME connection/transaction, so this fixture IS
            // visible to it, isolating the amount CHECK as the only thing that can fail.
            assertConstraintViolation(
                    List.of(
                            "INSERT INTO user_accounts (id, name, role) VALUES (900001, 'SetupCustomer', 'CUSTOMER')",
                            """
                            INSERT INTO orders (id, customer_id, base_coffee_type, status, applied_loyalty_tier,
                                price_base, price_extras, price_discount, price_total, placed_at, updated_at)
                            VALUES (900001, 900001, 'ESPRESSO', 'PLACED', 'REGULAR', 2.50, 0.00, 0.00, 2.50, now(), now())
                            """),
                    """
                    INSERT INTO payments (order_id, provider, amount, status, created_at, updated_at)
                    VALUES (900001, 'CASH', 0.00, 'PAID', now(), now())
                    """,
                    CHECK_VIOLATION, "chk_payment_amount_positive");
        }
    }

    @Nested
    @DisplayName("findByOrderId()")
    class FindByOrderIdTests {

        @Test
        @DisplayName("returns the payment for that order")
        void returnsPayment() {
            Instant now = Instant.now();
            paymentRepository.saveAndFlush(new PaymentEntity(
                    order, PaymentProvider.PAYPAL, new BigDecimal("2.50"), PaymentStatus.PAID, null, now, now));

            assertTrue(paymentRepository.findByOrderId(order.id()).isPresent());
        }

        @Test
        @DisplayName("returns empty for an order with no payment yet")
        void emptyWhenNoPayment() {
            assertTrue(paymentRepository.findByOrderId(order.id()).isEmpty());
        }
    }
}
