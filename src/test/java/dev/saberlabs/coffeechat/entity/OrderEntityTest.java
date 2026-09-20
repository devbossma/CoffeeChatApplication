package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderEntity")
class OrderEntityTest {

    private static final UserEntity CUSTOMER = new UserEntity("Alice", Role.CUSTOMER);
    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.50"), new BigDecimal("0.00"));
    private static final Instant NOW = Instant.now();

    private static void assignId(OrderEntity entity, long id) throws Exception {
        Field field = OrderEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static OrderEntity newOrder() {
        return new OrderEntity(CUSTOMER, CoffeeType.ESPRESSO, List.of(ExtraType.MILK),
                OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, NOW, NOW);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field, copying extras and the price breakdown")
        void capturesFields() {
            OrderEntity order = newOrder();
            assertEquals(CUSTOMER, order.customer());
            assertEquals(CoffeeType.ESPRESSO, order.baseCoffeeType());
            assertEquals(List.of(ExtraType.MILK), order.extras());
            assertEquals(OrderStatus.PLACED, order.status());
            assertEquals(LoyaltyTier.REGULAR, order.appliedLoyaltyTier());
            assertEquals(PRICE, order.price());
            assertEquals(NOW, order.placedAt());
            assertEquals(NOW, order.updatedAt());
            assertEquals(0, order.version());
        }

        @Test
        @DisplayName("rejects a null customer")
        void rejectsNullCustomer() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    null, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null base coffee type")
        void rejectsNullCoffeeType() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, null, List.of(), OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, NOW, NOW));
        }

        @Test
        @DisplayName("rejects null extras")
        void rejectsNullExtras() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, null, OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, List.of(), null, LoyaltyTier.REGULAR, PRICE, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null applied loyalty tier")
        void rejectsNullLoyaltyTier() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED, null, PRICE, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null price breakdown")
        void rejectsNullPrice() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED, LoyaltyTier.REGULAR, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null placedAt")
        void rejectsNullPlacedAt() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, null, NOW));
        }

        @Test
        @DisplayName("rejects a null updatedAt")
        void rejectsNullUpdatedAt() {
            assertThrows(NullPointerException.class, () -> new OrderEntity(
                    CUSTOMER, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED, LoyaltyTier.REGULAR, PRICE, NOW, null));
        }
    }

    @Nested
    @DisplayName("status(OrderStatus)")
    class StatusSetterTests {

        @Test
        @DisplayName("updates the status")
        void updatesStatus() {
            OrderEntity order = newOrder();
            order.status(OrderStatus.PREPARING);
            assertEquals(OrderStatus.PREPARING, order.status());
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            OrderEntity order = newOrder();
            assertThrows(NullPointerException.class, () -> order.status(null));
        }
    }

    @Nested
    @DisplayName("updatedAt(Instant)")
    class UpdatedAtSetterTests {

        @Test
        @DisplayName("updates the timestamp")
        void updatesTimestamp() {
            OrderEntity order = newOrder();
            Instant later = NOW.plusSeconds(60);
            order.updatedAt(later);
            assertEquals(later, order.updatedAt());
        }

        @Test
        @DisplayName("rejects a null timestamp")
        void rejectsNullTimestamp() {
            OrderEntity order = newOrder();
            assertThrows(NullPointerException.class, () -> order.updatedAt(null));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            OrderEntity order = newOrder();
            assertEquals(order, order);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            OrderEntity a = newOrder();
            OrderEntity b = newOrder();
            assignId(a, 5L);
            assignId(b, 5L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            OrderEntity a = newOrder();
            OrderEntity b = newOrder();
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal")
        void idlessNotEqual() {
            assertNotEquals(newOrder(), newOrder());
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            OrderEntity order = newOrder();
            assertNotEquals(order, "not an order");
            assertNotEquals(null, order);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the coffee type and status")
        void includesKeyFields() {
            String text = newOrder().toString();
            assertTrue(text.contains("ESPRESSO"));
            assertTrue(text.contains("PLACED"));
        }
    }
}
