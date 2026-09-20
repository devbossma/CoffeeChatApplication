package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderRepository")
class OrderRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity customer;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        userRepository.deleteAll();
        customer = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
    }

    private OrderEntity espressoOrder(List<ExtraType> extras) {
        Instant now = Instant.now();
        PriceBreakdown price = PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        return new OrderEntity(customer, CoffeeType.ESPRESSO, extras, OrderStatus.PLACED,
                LoyaltyTier.REGULAR, price, now, now);
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves and reloads an order, preserving extras order and multiplicity")
        void savesAndReloadsWithExtras() {
            OrderEntity saved = orderRepository.saveAndFlush(
                    espressoOrder(List.of(ExtraType.MILK, ExtraType.MILK, ExtraType.SUGAR)));
            entityManager.clear();

            OrderEntity reloaded = orderRepository.findById(saved.id()).orElseThrow();
            assertEquals(List.of(ExtraType.MILK, ExtraType.MILK, ExtraType.SUGAR), reloaded.extras());
        }

        @Test
        @DisplayName("rejects a customer_id that does not exist (foreign key violation)")
        void rejectsUnknownCustomer() {
            assertConstraintViolation("""
                    INSERT INTO orders (customer_id, base_coffee_type, status, applied_loyalty_tier,
                        price_base, price_extras, price_discount, price_total, placed_at, updated_at)
                    VALUES (404, 'ESPRESSO', 'PLACED', 'REGULAR', 2.50, 0.00, 0.00, 2.50, now(), now())
                    """, FOREIGN_KEY_VIOLATION, "fk_orders_customer");
        }

        @Test
        @DisplayName("a PriceBreakdown at the HALF_UP rounding boundary never violates chk_order_price_consistent")
        void roundingBoundaryNeverViolatesPriceConsistency() {
            // 10% of 3.75 = 0.375, which HALF_UP-rounds to 0.38 -- exactly the boundary case.
            PriceBreakdown price = PriceBreakdown.of(
                    new BigDecimal("3.75"), new BigDecimal("0.00"), new BigDecimal("0.375"));
            OrderEntity order = new OrderEntity(customer, CoffeeType.LATTE, List.of(), OrderStatus.PLACED,
                    LoyaltyTier.SILVER, price, Instant.now(), Instant.now());

            OrderEntity saved = orderRepository.saveAndFlush(order);
            entityManager.clear();

            OrderEntity reloaded = orderRepository.findById(saved.id()).orElseThrow();
            assertEquals(new BigDecimal("3.37"), reloaded.price().total());
        }
    }

    @Nested
    @DisplayName("findByStatusIn()")
    class FindByStatusInTests {

        @Test
        @DisplayName("returns only orders in the requested statuses (serves restart recovery)")
        void returnsMatchingOrders() {
            OrderEntity placed = orderRepository.saveAndFlush(espressoOrder(List.of()));
            OrderEntity preparing = espressoOrder(List.of());
            preparing.status(OrderStatus.PREPARING);
            preparing = orderRepository.saveAndFlush(preparing);
            OrderEntity fulfilled = espressoOrder(List.of());
            fulfilled.status(OrderStatus.FULFILLED);
            orderRepository.saveAndFlush(fulfilled);

            List<OrderEntity> orphaned = orderRepository.findByStatusIn(
                    List.of(OrderStatus.PLACED, OrderStatus.PREPARING));

            assertEquals(2, orphaned.size());
            assertTrue(orphaned.stream().map(OrderEntity::id).toList()
                    .containsAll(List.of(placed.id(), preparing.id())));
        }

        @Test
        @DisplayName("returns an empty list when nothing matches")
        void emptyWhenNoneMatch() {
            orderRepository.saveAndFlush(espressoOrder(List.of()));
            assertTrue(orderRepository.findByStatusIn(List.of(OrderStatus.CANCELLED)).isEmpty());
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("does not dereference an uninitialized lazy customer proxy on a detached entity")
        void safeOnDetachedLazyProxy() {
            OrderEntity saved = orderRepository.saveAndFlush(espressoOrder(List.of()));
            entityManager.clear();

            OrderEntity reloaded = orderRepository.findById(saved.id()).orElseThrow();
            entityManager.detach(reloaded);

            String text = assertDoesNotThrow(reloaded::toString);
            assertTrue(text.contains("<lazy>"));
        }
    }

    @Nested
    @DisplayName("optimistic locking (@Version)")
    class OptimisticLockingTests {

        @Test
        @DisplayName("a stale write loses to a concurrent update on the same row")
        void staleWriteConflicts() {
            OrderEntity saved = orderRepository.saveAndFlush(espressoOrder(List.of()));
            Long id = saved.id();
            entityManager.clear();

            // Fetch B first and detach it immediately (clear()), so it is not the persistence
            // context's live managed instance for this id when A is fetched/updated next --
            // otherwise Spring Data's merge-on-save of a detached A would reconcile into B's
            // still-managed instance and silently bump B's version too, masking the conflict.
            OrderEntity copyB = orderRepository.findById(id).orElseThrow();
            entityManager.clear();

            OrderEntity copyA = orderRepository.findById(id).orElseThrow();
            copyA.status(OrderStatus.PREPARING);
            orderRepository.saveAndFlush(copyA);
            entityManager.clear();

            // copyB is fully detached and still carries the pre-update version.
            copyB.status(OrderStatus.CANCELLED);
            assertThrows(ObjectOptimisticLockingFailureException.class,
                    () -> orderRepository.saveAndFlush(copyB));
        }
    }
}
