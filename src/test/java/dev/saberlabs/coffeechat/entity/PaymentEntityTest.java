package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
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

@DisplayName("PaymentEntity")
class PaymentEntityTest {

    private static final Instant NOW = Instant.now();
    private static final OrderEntity ORDER = new OrderEntity(
            new UserEntity("Alice", Role.CUSTOMER), CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED,
            LoyaltyTier.REGULAR, PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO),
            NOW, NOW);

    private static void assignId(PaymentEntity entity, long id) throws Exception {
        Field field = PaymentEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static PaymentEntity newPayment(String detail) {
        return new PaymentEntity(ORDER, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID, detail, NOW, NOW);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field")
        void capturesFields() {
            PaymentEntity payment = newPayment("change $0.00");
            assertEquals(ORDER, payment.order());
            assertEquals(PaymentProvider.CASH, payment.provider());
            assertEquals(new BigDecimal("2.50"), payment.amount());
            assertEquals(PaymentStatus.PAID, payment.status());
            assertEquals("change $0.00", payment.detail());
            assertEquals(NOW, payment.createdAt());
            assertEquals(NOW, payment.updatedAt());
        }

        @Test
        @DisplayName("allows a null detail")
        void allowsNullDetail() {
            assertNull(newPayment(null).detail());
        }

        @Test
        @DisplayName("rejects a null order")
        void rejectsNullOrder() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    null, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null provider")
        void rejectsNullProvider() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    ORDER, null, new BigDecimal("2.50"), PaymentStatus.PAID, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null amount")
        void rejectsNullAmount() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    ORDER, PaymentProvider.CASH, null, PaymentStatus.PAID, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    ORDER, PaymentProvider.CASH, new BigDecimal("2.50"), null, null, NOW, NOW));
        }

        @Test
        @DisplayName("rejects a null createdAt")
        void rejectsNullCreatedAt() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    ORDER, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID, null, null, NOW));
        }

        @Test
        @DisplayName("rejects a null updatedAt")
        void rejectsNullUpdatedAt() {
            assertThrows(NullPointerException.class, () -> new PaymentEntity(
                    ORDER, PaymentProvider.CASH, new BigDecimal("2.50"), PaymentStatus.PAID, null, NOW, null));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            PaymentEntity payment = newPayment(null);
            assertEquals(payment, payment);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            PaymentEntity a = newPayment(null);
            PaymentEntity b = newPayment(null);
            assignId(a, 9L);
            assignId(b, 9L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            PaymentEntity a = newPayment(null);
            PaymentEntity b = newPayment(null);
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal")
        void idlessNotEqual() {
            assertNotEquals(newPayment(null), newPayment(null));
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            PaymentEntity payment = newPayment(null);
            assertNotEquals(payment, "not a payment");
            assertNotEquals(null, payment);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the provider and status")
        void includesKeyFields() {
            String text = newPayment(null).toString();
            assertTrue(text.contains("CASH"));
            assertTrue(text.contains("PAID"));
        }
    }
}
