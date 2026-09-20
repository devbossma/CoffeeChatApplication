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

            assertThrows(RuntimeException.class, () -> paymentRepository.saveAndFlush(new PaymentEntity(
                    order, PaymentProvider.STRIPE, new BigDecimal("2.50"), PaymentStatus.PAID, null, now, now)));
        }

        @Test
        @DisplayName("rejects a non-positive amount at the database level")
        void rejectsNonPositiveAmount() {
            assertConstraintViolation("""
                    INSERT INTO payments (order_id, provider, amount, status, created_at, updated_at)
                    VALUES (%d, 'CASH', 0.00, 'PAID', now(), now())
                    """.formatted(order.id()));
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
