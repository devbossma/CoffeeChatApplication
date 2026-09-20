package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.CoffeeType;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderStatusHistoryEntity")
class OrderStatusHistoryEntityTest {

    private static final Instant NOW = Instant.now();
    private static final OrderEntity ORDER = new OrderEntity(
            new UserEntity("Alice", Role.CUSTOMER), CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED,
            LoyaltyTier.REGULAR, PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO),
            NOW, NOW);
    private static final UserEntity BARISTA = new UserEntity("Bob", Role.BARISTA);

    private static void assignId(OrderStatusHistoryEntity entity, long id) throws Exception {
        Field field = OrderStatusHistoryEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static OrderStatusHistoryEntity newHistory() {
        return new OrderStatusHistoryEntity(ORDER, OrderStatus.PREPARING, OrderStatus.READY, NOW, BARISTA);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field for a barista-attributed transition")
        void capturesBaristaTransition() {
            OrderStatusHistoryEntity history = newHistory();
            assertEquals(ORDER, history.order());
            assertEquals(OrderStatus.PREPARING, history.fromStatus());
            assertEquals(OrderStatus.READY, history.toStatus());
            assertEquals(NOW, history.changedAt());
            assertEquals(BARISTA, history.changedBy());
        }

        @Test
        @DisplayName("allows a null fromStatus and a null changedBy (system transition)")
        void allowsSystemTransition() {
            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(ORDER, null, OrderStatus.PLACED, NOW, null);
            assertNull(history.fromStatus());
            assertNull(history.changedBy());
        }

        @Test
        @DisplayName("rejects a null order")
        void rejectsNullOrder() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusHistoryEntity(null, OrderStatus.PLACED, OrderStatus.PREPARING, NOW, BARISTA));
        }

        @Test
        @DisplayName("rejects a null toStatus")
        void rejectsNullToStatus() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusHistoryEntity(ORDER, OrderStatus.PLACED, null, NOW, BARISTA));
        }

        @Test
        @DisplayName("rejects a null changedAt")
        void rejectsNullChangedAt() {
            assertThrows(NullPointerException.class,
                    () -> new OrderStatusHistoryEntity(ORDER, OrderStatus.PLACED, OrderStatus.PREPARING, null, BARISTA));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            OrderStatusHistoryEntity history = newHistory();
            assertEquals(history, history);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            OrderStatusHistoryEntity a = newHistory();
            OrderStatusHistoryEntity b = newHistory();
            assignId(a, 3L);
            assignId(b, 3L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            OrderStatusHistoryEntity a = newHistory();
            OrderStatusHistoryEntity b = newHistory();
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal")
        void idlessNotEqual() {
            assertNotEquals(newHistory(), newHistory());
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            OrderStatusHistoryEntity history = newHistory();
            assertNotEquals(history, "not a history entry");
            assertNotEquals(null, history);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the transition and the barista's id when set")
        void includesBaristaTransition() {
            String text = newHistory().toString();
            assertTrue(text.contains("PREPARING"));
            assertTrue(text.contains("READY"));
        }

        @Test
        @DisplayName("shows 'system' for a null actor")
        void showsSystemForNullActor() {
            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(ORDER, null, OrderStatus.PLACED, NOW, null);
            assertTrue(history.toString().contains("system"));
        }
    }
}
