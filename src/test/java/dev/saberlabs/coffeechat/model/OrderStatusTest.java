package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderStatus")
class OrderStatusTest {

    @Nested
    @DisplayName("canTransitionTo()")
    class CanTransitionToTests {

        @Test
        @DisplayName("PLACED may advance to PREPARING or CANCELLED")
        void placedForwards() {
            assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.PREPARING));
            assertTrue(OrderStatus.PLACED.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("PREPARING may advance to READY or CANCELLED")
        void preparingForwards() {
            assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.READY));
            assertTrue(OrderStatus.PREPARING.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("READY may advance to FULFILLED or CANCELLED")
        void readyForwards() {
            assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.FULFILLED));
            assertTrue(OrderStatus.READY.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("the lifecycle cannot run backwards")
        void noBackwards() {
            assertFalse(OrderStatus.READY.canTransitionTo(OrderStatus.PREPARING));
            assertFalse(OrderStatus.PREPARING.canTransitionTo(OrderStatus.PLACED));
        }

        @Test
        @DisplayName("FULFILLED is terminal")
        void fulfilledTerminal() {
            assertFalse(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.READY));
            assertFalse(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("CANCELLED is terminal")
        void cancelledTerminal() {
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PREPARING));
            assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.FULFILLED));
        }

        @Test
        @DisplayName("a null target is never a legal transition")
        void nullTarget() {
            assertFalse(OrderStatus.PLACED.canTransitionTo(null));
        }
    }
}
