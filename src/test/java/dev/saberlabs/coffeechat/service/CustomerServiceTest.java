package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CustomerService")
class CustomerServiceTest extends AbstractIntegrationTest {

    @Autowired CustomerService service;
    @Autowired TransactionTemplate tx;

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("persists a CUSTOMER with 0 fulfilled orders and a derived REGULAR tier")
        void creates() {
            UserEntity created = service.create("Alice");

            assertNotNull(created.id());
            UserEntity reloaded = users.findById(created.id()).orElseThrow();
            assertEquals(Role.CUSTOMER, reloaded.role());
            assertEquals(0, reloaded.fulfilledOrders());
            assertEquals(LoyaltyTier.REGULAR, reloaded.loyaltyTier());
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> service.create("   "));
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> service.create(null));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("finds a customer")
        void finds() {
            UserEntity created = service.create("Alice");
            assertTrue(service.findById(created.id()).isPresent());
        }

        @Test
        @DisplayName("is empty for an unknown id")
        void unknown() {
            assertTrue(service.findById(404L).isEmpty());
        }

        @Test
        @DisplayName("is empty for a user who is not a CUSTOMER")
        void notACustomer() {
            UserEntity barista = users.save(new UserEntity("Bob", Role.BARISTA));
            assertTrue(service.findById(barista.id()).isEmpty());
        }

        @Test
        @DisplayName("rejects a null id")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> service.findById(null));
        }
    }

    @Nested
    @DisplayName("require()")
    class RequireTests {

        @Test
        @DisplayName("returns the customer inside a transaction")
        void requires() {
            UserEntity created = service.create("Alice");
            Long id = tx.execute(status -> service.require(created.id()).id());
            assertEquals(created.id(), id);
        }

        @Test
        @DisplayName("throws CustomerNotFoundException for an unknown or non-customer id")
        void notFound() {
            UserEntity barista = users.save(new UserEntity("Bob", Role.BARISTA));
            assertThrows(CustomerNotFoundException.class, () -> tx.execute(status -> service.require(404L)));
            assertThrows(CustomerNotFoundException.class, () -> tx.execute(status -> service.require(barista.id())));
        }

        @Test
        @DisplayName("requires an existing transaction (MANDATORY)")
        void requiresTransaction() {
            assertThrows(IllegalTransactionStateException.class, () -> service.require(1L));
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null repository")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> new CustomerService(null));
        }
    }
}
