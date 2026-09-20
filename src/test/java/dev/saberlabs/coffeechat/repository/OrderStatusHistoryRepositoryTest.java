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
            // A fresh customer+order is inserted here, not the @BeforeEach fixture: that fixture
            // lives in this test's own uncommitted JPA transaction and is invisible to
            // assertConstraintViolation's separate connection, which would otherwise fail on the
            // order_id FK instead of the changed_by FK this test names. Setup and violating
            // statement share one connection/transaction, isolating changed_by as the only FK that
            // can fail (order_id=900002 is valid within it; changed_by=404 genuinely is not).
            assertConstraintViolation(
                    List.of(
                            "INSERT INTO user_accounts (id, name, role) VALUES (900002, 'SetupCustomer', 'CUSTOMER')",
                            """
                            INSERT INTO orders (id, customer_id, base_coffee_type, status, applied_loyalty_tier,
                                price_base, price_extras, price_discount, price_total, placed_at, updated_at)
                            VALUES (900002, 900002, 'ESPRESSO', 'PLACED', 'REGULAR', 2.50, 0.00, 0.00, 2.50, now(), now())
                            """),
                    "INSERT INTO order_status_history (order_id, to_status, changed_at, changed_by) VALUES (900002, 'READY', now(), 404)",
                    FOREIGN_KEY_VIOLATION, "fk_order_status_history_changed_by");
        }
    }

    @Nested
    @DisplayName("findByOrderIdOrderByChangedAtAscIdAsc()")
    class FindByOrderIdOrderByChangedAtAscTests {

        @Test
        @DisplayName("returns the order's audit trail oldest first")
        void returnsOldestFirst() {
            Instant base = Instant.now();
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, null, OrderStatus.PLACED, base, null));
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, OrderStatus.PLACED, OrderStatus.PREPARING, base.plusSeconds(1), barista));
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(
                    order, OrderStatus.PREPARING, OrderStatus.READY, base.plusSeconds(2), barista));

            List<OrderStatusHistoryEntity> trail = historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.id());

            assertEquals(3, trail.size());
            assertEquals(OrderStatus.PLACED, trail.get(0).toStatus());
            assertEquals(OrderStatus.PREPARING, trail.get(1).toStatus());
            assertEquals(OrderStatus.READY, trail.get(2).toStatus());
        }

        @Test
        @DisplayName("rows written at the very same instant keep their write order (id breaks the tie)")
        void sameInstantKeepsWriteOrder() {
            Instant same = Instant.now();
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(order, null, OrderStatus.PLACED, same, null));
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(order, OrderStatus.PLACED, OrderStatus.PREPARING, same, null));
            historyRepository.saveAndFlush(new OrderStatusHistoryEntity(order, OrderStatus.PREPARING, OrderStatus.READY, same, null));

            List<OrderStatusHistoryEntity> trail = historyRepository.findByOrderIdOrderByChangedAtAscIdAsc(order.id());

            assertEquals(List.of(OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY),
                    trail.stream().map(OrderStatusHistoryEntity::toStatus).toList());
        }
    }
}
