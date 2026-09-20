package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderService")
class OrderServiceTest extends AbstractIntegrationTest {

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("3.50"), new BigDecimal("0.50"), new BigDecimal("0.00"));

    @Autowired OrderService service;
    @Autowired TransactionTemplate tx;
    @Autowired EntityManagerFactory entityManagerFactory;

    private OrderEntity createInTx(UserEntity customer, List<ExtraType> extras) {
        return tx.execute(status -> service.create(customer, CoffeeType.LATTE, extras, LoyaltyTier.SILVER, PRICE));
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("inserts a PLACED order with the frozen tier, price and ordered extras")
        void inserts() {
            UserEntity customer = customer("Alice");

            OrderEntity created = createInTx(customer, List.of(ExtraType.MILK, ExtraType.MILK));

            assertNotNull(created.id());
            Order snapshot = service.findById(created.id()).orElseThrow();
            assertEquals(OrderStatus.PLACED, snapshot.status());
            assertEquals(LoyaltyTier.SILVER, snapshot.appliedLoyaltyTier());
            assertEquals(PRICE, snapshot.price());
            assertEquals(List.of(ExtraType.MILK, ExtraType.MILK), snapshot.extras());
            assertEquals(customer.id(), snapshot.customerId());
        }

        @Test
        @DisplayName("requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            UserEntity customer = customer("Alice");
            assertThrows(IllegalTransactionStateException.class,
                    () -> service.create(customer, CoffeeType.LATTE, List.of(), LoyaltyTier.REGULAR, PRICE));
        }
    }

    @Nested
    @DisplayName("require()")
    class RequireTests {

        @Test
        @DisplayName("loads the managed entity inside a transaction")
        void loads() {
            UserEntity customer = customer("Alice");
            Long id = createInTx(customer, List.of()).id();

            Long loaded = tx.execute(status -> service.require(id).id());

            assertEquals(id, loaded);
        }

        @Test
        @DisplayName("throws OrderNotFoundException for an unknown id")
        void unknown() {
            assertThrows(OrderNotFoundException.class, () -> tx.execute(status -> service.require(404L)));
        }

        @Test
        @DisplayName("requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            assertThrows(IllegalTransactionStateException.class, () -> service.require(1L));
        }

        @Test
        @DisplayName("rejects a null id")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> tx.execute(status -> service.require(null)));
        }
    }

    @Nested
    @DisplayName("flush()")
    class FlushTests {

        @Test
        @DisplayName("requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            assertThrows(IllegalTransactionStateException.class, () -> service.flush());
        }

        @Test
        @DisplayName("writes pending changes inside a transaction")
        void flushes() {
            UserEntity customer = customer("Alice");
            Long id = createInTx(customer, List.of()).id();

            tx.executeWithoutResult(status -> {
                service.require(id).transitionTo(OrderStatus.PREPARING);
                service.flush();
                assertEquals("PREPARING", jdbc.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, id));
            });
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("returns an immutable snapshot, including the derived coffee description")
        void snapshot() {
            UserEntity customer = customer("Alice");
            Long id = createInTx(customer, List.of(ExtraType.SUGAR)).id();

            Order snapshot = service.findById(id).orElseThrow();

            assertTrue(snapshot.coffeeDescription().toLowerCase().contains("latte"));
            assertThrows(UnsupportedOperationException.class, () -> snapshot.extras().add(ExtraType.MILK));
        }

        @Test
        @DisplayName("is empty for an unknown id")
        void empty() {
            assertTrue(service.findById(404L).isEmpty());
        }
    }

    @Nested
    @DisplayName("findByCustomer()")
    class FindByCustomerTests {

        @Test
        @DisplayName("returns only that customer's orders")
        void filtersByCustomer() {
            UserEntity alice = customer("Alice");
            UserEntity bob = customer("Bob");
            createInTx(alice, List.of());
            createInTx(alice, List.of());
            createInTx(bob, List.of());

            assertEquals(2, service.findByCustomer(alice.id()).size());
            assertEquals(1, service.findByCustomer(bob.id()).size());
        }

        @Test
        @DisplayName("is empty for a customer with no orders")
        void empty() {
            assertTrue(service.findByCustomer(customer("Alice").id()).isEmpty());
        }

        @Test
        @DisplayName("maps many orders with ONE SQL statement: no per-order query for the customer proxy or the extras")
        void oneStatementRegardlessOfOrderCount() {
            UserEntity alice = customer("Alice");
            for (int i = 0; i < 5; i++) {
                createInTx(alice, List.of(ExtraType.MILK, ExtraType.SUGAR));
            }
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            List<Order> snapshots = service.findByCustomer(alice.id());

            assertEquals(5, snapshots.size());
            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR), snapshots.get(0).extras());
            assertEquals(alice.id(), snapshots.get(0).customerId());
            assertEquals(1, statistics.getPrepareStatementCount());
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects null collaborators")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> new OrderService(null, new OrderMapper(new dev.saberlabs.coffeechat.factory.CoffeeFactory())));
            assertThrows(NullPointerException.class, () -> new OrderService(orders, null));
        }
    }
}
