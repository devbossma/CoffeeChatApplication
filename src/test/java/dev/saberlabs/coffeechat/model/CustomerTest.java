package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Customer")
class CustomerTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("starts with no id and 0 fulfilled orders")
        void startsEmpty() {
            Customer customer = new Customer("Alice");
            assertNull(customer.id());
            assertEquals(0, customer.fulfilledOrders());
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsNullName() {
            assertThrows(IllegalArgumentException.class, () -> new Customer(null));
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThrows(IllegalArgumentException.class, () -> new Customer("   "));
        }
    }

    @Nested
    @DisplayName("assignId()")
    class AssignIdTests {

        @Test
        @DisplayName("sets the id when none is assigned yet")
        void setsId() {
            Customer customer = new Customer("Alice");
            customer.assignId(42L);
            assertEquals(42L, customer.id());
        }

        @Test
        @DisplayName("throws if an id was already assigned")
        void rejectsSecondAssignment() {
            Customer customer = new Customer("Alice");
            customer.assignId(1L);
            assertThrows(IllegalStateException.class, () -> customer.assignId(2L));
        }
    }

    @Nested
    @DisplayName("loyaltyTier()")
    class LoyaltyTierTests {

        @Test
        @DisplayName("a brand-new customer is REGULAR")
        void newCustomerIsRegular() {
            assertEquals(LoyaltyTier.REGULAR, new Customer("Alice").loyaltyTier());
        }

        @Test
        @DisplayName("derives SILVER after the 6th fulfilled order")
        void becomesSilver() {
            Customer customer = new Customer("Alice");
            for (int i = 0; i < 6; i++) {
                customer.incrementFulfilled();
            }
            assertEquals(LoyaltyTier.SILVER, customer.loyaltyTier());
        }

        @Test
        @DisplayName("derives GOLD after the 11th fulfilled order")
        void becomesGold() {
            Customer customer = new Customer("Alice");
            for (int i = 0; i < 11; i++) {
                customer.incrementFulfilled();
            }
            assertEquals(LoyaltyTier.GOLD, customer.loyaltyTier());
        }
    }

    @Nested
    @DisplayName("incrementFulfilled()")
    class IncrementFulfilledTests {

        @Test
        @DisplayName("raises the fulfilled-order count by one")
        void raisesCount() {
            Customer customer = new Customer("Alice");
            customer.incrementFulfilled();
            customer.incrementFulfilled();
            assertEquals(2, customer.fulfilledOrders());
        }

        @Test
        @DisplayName("crossing a threshold flips the derived tier")
        void flipsTierAtBoundary() {
            Customer customer = new Customer("Alice");
            for (int i = 0; i < 5; i++) {
                customer.incrementFulfilled();
            }
            assertEquals(LoyaltyTier.REGULAR, customer.loyaltyTier());
            customer.incrementFulfilled();
            assertEquals(LoyaltyTier.SILVER, customer.loyaltyTier());
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the name and the derived tier")
        void includesKeyFields() {
            Customer customer = new Customer("Alice");
            customer.assignId(3L);
            String text = customer.toString();
            assertTrue(text.contains("Alice"));
            assertTrue(text.contains("REGULAR"));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("two customers with the same assigned id are equal")
        void sameIdEqual() {
            Customer a = new Customer("Alice");
            Customer b = new Customer("Alice's twin");
            a.assignId(7L);
            b.assignId(7L);
            assertEquals(a, b);
        }

        @Test
        @DisplayName("two customers with different ids are not equal")
        void differentIdNotEqual() {
            Customer a = new Customer("Alice");
            Customer b = new Customer("Bob");
            a.assignId(1L);
            b.assignId(2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less customers are not equal (no identity to compare)")
        void idlessNotEqual() {
            assertNotEquals(new Customer("Alice"), new Customer("Alice"));
        }
    }
}
