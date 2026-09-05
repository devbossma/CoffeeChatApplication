package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Order")
class OrderTest {

    private static Customer customer() {
        Customer c = new Customer("Alice");
        c.assignId(1L);
        return c;
    }

    private static Order espressoOrder(List<ExtraType> extras) {
        return new Order(
                customer(),
                new Espresso(),
                CoffeeType.ESPRESSO,
                extras,
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("builds an order that starts with a null status and no id")
        void startsUnplaced() {
            Order order = espressoOrder(List.of());
            assertNull(order.status());
            assertNull(order.id());
        }

        @Test
        @DisplayName("defensively copies the extras list")
        void copiesExtras() {
            List<ExtraType> mutable = new ArrayList<>(List.of(ExtraType.MILK));
            Order order = espressoOrder(mutable);
            mutable.add(ExtraType.SUGAR);
            assertEquals(List.of(ExtraType.MILK), order.extras());
        }

        @Test
        @DisplayName("returns an unmodifiable extras list")
        void extrasUnmodifiable() {
            Order order = espressoOrder(List.of(ExtraType.MILK));
            assertThrows(UnsupportedOperationException.class,
                    () -> order.extras().add(ExtraType.SUGAR));
        }

        @Test
        @DisplayName("rejects a null customer")
        void rejectsNullCustomer() {
            assertThrows(NullPointerException.class, () -> new Order(
                    null, new Espresso(), CoffeeType.ESPRESSO, List.of(),
                    PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO),
                    LoyaltyTier.REGULAR));
        }

        @Test
        @DisplayName("rejects a coffee whose type disagrees with baseType")
        void rejectsTypeMismatch() {
            assertThrows(IllegalArgumentException.class, () -> new Order(
                    customer(), new Espresso(), CoffeeType.LATTE, List.of(),
                    PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO),
                    LoyaltyTier.REGULAR));
        }
    }

    @Nested
    @DisplayName("assignId()")
    class AssignIdTests {

        @Test
        @DisplayName("sets the id once")
        void setsId() {
            Order order = espressoOrder(List.of());
            order.assignId(5L);
            assertEquals(5L, order.id());
        }

        @Test
        @DisplayName("rejects a second id assignment")
        void rejectsSecondAssignment() {
            Order order = espressoOrder(List.of());
            order.assignId(5L);
            assertThrows(IllegalStateException.class, () -> order.assignId(6L));
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the customer name, coffee description and total")
        void includesKeyFields() {
            Order order = espressoOrder(List.of());
            String text = order.toString();
            assertTrue(text.contains("Alice"));
            assertTrue(text.contains("Espresso"));
            assertTrue(text.contains("2.50"));
        }
    }

    @Nested
    @DisplayName("transitionTo()")
    class TransitionToTests {

        @Test
        @DisplayName("null -> PLACED is the only legal first move, and stamps placedAt")
        void firstMoveToPlaced() {
            Order order = espressoOrder(List.of());
            order.transitionTo(OrderStatus.PLACED);
            assertEquals(OrderStatus.PLACED, order.status());
            assertNotNull(order.placedAt());
            assertNotNull(order.updatedAt());
        }

        @Test
        @DisplayName("rejects any first move other than PLACED")
        void firstMoveMustBePlaced() {
            Order order = espressoOrder(List.of());
            assertThrows(IllegalStateException.class, () -> order.transitionTo(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("walks the full happy-path lifecycle")
        void happyPath() {
            Order order = espressoOrder(List.of());
            order.transitionTo(OrderStatus.PLACED);
            order.transitionTo(OrderStatus.PREPARING);
            order.transitionTo(OrderStatus.READY);
            order.transitionTo(OrderStatus.FULFILLED);
            assertEquals(OrderStatus.FULFILLED, order.status());
        }

        @Test
        @DisplayName("rejects an illegal jump")
        void rejectsIllegalJump() {
            Order order = espressoOrder(List.of());
            order.transitionTo(OrderStatus.PLACED);
            assertThrows(IllegalStateException.class, () -> order.transitionTo(OrderStatus.READY));
        }

        @Test
        @DisplayName("rejects a transition out of a terminal state")
        void rejectsMoveFromTerminal() {
            Order order = espressoOrder(List.of());
            order.transitionTo(OrderStatus.PLACED);
            order.transitionTo(OrderStatus.CANCELLED);
            assertThrows(IllegalStateException.class, () -> order.transitionTo(OrderStatus.PREPARING));
        }

        @Test
        @DisplayName("rejects a null target")
        void rejectsNullTarget() {
            Order order = espressoOrder(List.of());
            assertThrows(NullPointerException.class, () -> order.transitionTo(null));
        }
    }
}
