package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
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
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("OrderStatusHistoryRepository")
class OrderStatusHistoryRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private OrderEntity order;
    private UserEntity barista;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity customer = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
        barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
        Instant now = Instant.now();
        PriceBreakdown price = PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        order = orderRepository.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(),
                OrderStatus.PLACED, LoyaltyTier.REGULAR, price, now, now));
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("records a barista-attributed transition")
        void recordsBaristaActor() {
            OrderStatusHistoryEntity saved = historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, OrderStatus.PREPARING, OrderStatus.READY, Instant.now(), barista));
            entityManager.clear();

            OrderStatusHistoryEntity reloaded = historyRepository.findById(saved.id()).orElseThrow();
            assertEquals(barista.id(), reloaded.changedBy().id());
        }

        @Test
        @DisplayName("records a system (automated) transition with a null actor")
        void recordsSystemTransition() {
            OrderStatusHistoryEntity saved = historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, null, OrderStatus.PLACED, Instant.now(), null));
            entityManager.clear();

            OrderStatusHistoryEntity reloaded = historyRepository.findById(saved.id()).orElseThrow();
            assertNull(reloaded.fromStatus());
            assertNull(reloaded.changedBy());
        }

        @Test
        @DisplayName("rejects a changed_by that does not reference an existing user (foreign key violation)")
        void rejectsUnknownChangedBy() {
            assertConstraintViolation("""
                    INSERT INTO order_status_history (order_id, to_status, changed_at, changed_by)
                    VALUES (%d, 'READY', now(), 404)
                    """.formatted(order.id()));
        }
    }

    @Nested
    @DisplayName("findByOrderIdOrderByChangedAtAsc()")
    class FindByOrderIdOrderByChangedAtAscTests {

        @Test
        @DisplayName("returns the order's audit trail oldest first")
        void returnsOldestFirst() throws InterruptedException {
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, null, OrderStatus.PLACED, Instant.now(), null));
            Thread.sleep(5);
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, OrderStatus.PLACED, OrderStatus.PREPARING, Instant.now(), barista));
            Thread.sleep(5);
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, OrderStatus.PREPARING, OrderStatus.READY, Instant.now(), barista));

            List<OrderStatusHistoryEntity> trail = historyRepository.findByOrderIdOrderByChangedAtAsc(order.id());

            assertEquals(3, trail.size());
            assertEquals(OrderStatus.PLACED, trail.get(0).toStatus());
            assertEquals(OrderStatus.PREPARING, trail.get(1).toStatus());
            assertEquals(OrderStatus.READY, trail.get(2).toStatus());
        }
    }
}
