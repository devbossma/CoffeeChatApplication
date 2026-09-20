package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code Order} is now an immutable snapshot record; the legality rules that used to live on the
 * mutable class ({@code transitionTo}/{@code restoreStatus}) moved to {@code OrderEntity} and are
 * tested in {@code OrderEntityTest}.
 */
@DisplayName("Order (snapshot)")
class OrderTest {

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.50"), new BigDecimal("0.00"));
    private static final Instant NOW = Instant.now();

    private static Order order(List<ExtraType> extras) {
        return new Order(1L, 2L, CoffeeType.ESPRESSO, extras, "Espresso", PRICE,
                LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field")
        void capturesFields() {
            Order order = order(List.of(ExtraType.MILK));
            assertEquals(1L, order.id());
            assertEquals(2L, order.customerId());
            assertEquals(CoffeeType.ESPRESSO, order.baseType());
            assertEquals(List.of(ExtraType.MILK), order.extras());
            assertEquals("Espresso", order.coffeeDescription());
            assertEquals(PRICE, order.price());
            assertEquals(LoyaltyTier.REGULAR, order.appliedLoyaltyTier());
            assertEquals(OrderStatus.PLACED, order.status());
            assertEquals(NOW, order.placedAt());
            assertEquals(NOW, order.updatedAt());
        }

        @Test
        @DisplayName("defensively copies the extras list")
        void copiesExtras() {
            List<ExtraType> mutable = new ArrayList<>(List.of(ExtraType.MILK));
            Order order = order(mutable);
            mutable.add(ExtraType.SUGAR);
            assertEquals(List.of(ExtraType.MILK), order.extras());
        }

        @Test
        @DisplayName("returns an unmodifiable extras list")
        void extrasUnmodifiable() {
            Order order = order(List.of(ExtraType.MILK));
            assertThrows(UnsupportedOperationException.class, () -> order.extras().add(ExtraType.SUGAR));
        }

        @Test
        @DisplayName("rejects a null id")
        void rejectsNullId() {
            assertThrows(NullPointerException.class, () -> new Order(null, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null customer id")
        void rejectsNullCustomerId() {
            assertThrows(NullPointerException.class, () -> new Order(1L, null, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null base type")
        void rejectsNullBaseType() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, null, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects null extras")
        void rejectsNullExtras() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, null,
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null description")
        void rejectsNullDescription() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    null, PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null price")
        void rejectsNullPrice() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", null, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null applied tier")
        void rejectsNullTier() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, null, OrderStatus.PLACED, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects null timestamps")
        void rejectsNullTimestamps() {
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, null, NOW));
            assertThrows(NullPointerException.class, () -> new Order(1L, 2L, CoffeeType.ESPRESSO, List.of(),
                    "Espresso", PRICE, LoyaltyTier.REGULAR, OrderStatus.PLACED, NOW, null));
        }
    }
}
