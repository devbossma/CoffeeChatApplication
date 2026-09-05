package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CustomerService")
class CustomerServiceTest {

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("creates a customer with an id and 0 fulfilled orders")
        void createsCustomer() {
            Customer created = customerService.create("Alice");
            assertNotNull(created.id());
            assertEquals(0, created.fulfilledOrders());
        }

        @Test
        @DisplayName("assigns distinct ids to successive customers")
        void distinctIds() {
            Customer a = customerService.create("Alice");
            Customer b = customerService.create("Bob");
            assertTrue(!a.id().equals(b.id()));
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> customerService.create("  "));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("returns a previously created customer")
        void findsExisting() {
            Customer created = customerService.create("Alice");
            assertEquals(created.id(), customerService.findById(created.id()).orElseThrow().id());
        }

        @Test
        @DisplayName("returns empty for an unknown id")
        void emptyForUnknown() {
            assertTrue(customerService.findById(404L).isEmpty());
        }
    }

    @Nested
    @DisplayName("incrementFulfilled()")
    class IncrementFulfilledTests {

        @Test
        @DisplayName("raises the customer's fulfilled-order count")
        void raisesCount() {
            Customer created = customerService.create("Alice");
            customerService.incrementFulfilled(created.id());
            assertEquals(1, customerService.findById(created.id()).orElseThrow().fulfilledOrders());
        }

        @Test
        @DisplayName("can raise the derived loyalty tier")
        void canRaiseTier() {
            Customer created = customerService.create("Alice");
            for (int i = 0; i < 6; i++) {
                customerService.incrementFulfilled(created.id());
            }
            assertEquals(LoyaltyTier.SILVER,
                    customerService.findById(created.id()).orElseThrow().loyaltyTier());
        }

        @Test
        @DisplayName("throws for an unknown customer id")
        void throwsForUnknown() {
            assertThrows(NoSuchElementException.class, () -> customerService.incrementFulfilled(404L));
        }
    }

    @Nested
    @DisplayName("clear()")
    class ClearTests {

        @Test
        @DisplayName("removes every stored customer")
        void removesAll() {
            Customer created = customerService.create("Alice");
            customerService.clear();
            assertTrue(customerService.findById(created.id()).isEmpty());
        }

        @Test
        @DisplayName("resets the id sequence")
        void resetsSequence() {
            customerService.create("Alice");
            customerService.clear();
            assertEquals(1L, customerService.create("Bob").id());
        }
    }
}
