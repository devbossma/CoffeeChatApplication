package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UserEntity")
class UserEntityTest {

    private static void assignId(UserEntity entity, long id) throws Exception {
        Field field = UserEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("starts with no id and 0 fulfilled orders")
        void startsEmpty() {
            UserEntity user = new UserEntity("Alice", Role.CUSTOMER);
            assertNull(user.id());
            assertEquals(0, user.fulfilledOrders());
            assertEquals("Alice", user.name());
            assertEquals(Role.CUSTOMER, user.role());
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsNullName() {
            assertThrows(NullPointerException.class, () -> new UserEntity(null, Role.CUSTOMER));
        }

        @Test
        @DisplayName("rejects a null role")
        void rejectsNullRole() {
            assertThrows(NullPointerException.class, () -> new UserEntity("Alice", null));
        }
    }

    @Nested
    @DisplayName("loyaltyTier()")
    class LoyaltyTierTests {

        @Test
        @DisplayName("a brand-new user is REGULAR")
        void newUserIsRegular() {
            assertEquals(LoyaltyTier.REGULAR, new UserEntity("Alice", Role.CUSTOMER).loyaltyTier());
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            UserEntity user = new UserEntity("Alice", Role.CUSTOMER);
            assertEquals(user, user);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            UserEntity a = new UserEntity("Alice", Role.CUSTOMER);
            UserEntity b = new UserEntity("Alice's twin", Role.BARISTA);
            assignId(a, 7L);
            assignId(b, 7L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            UserEntity a = new UserEntity("Alice", Role.CUSTOMER);
            UserEntity b = new UserEntity("Bob", Role.CUSTOMER);
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal (no identity to compare)")
        void idlessNotEqual() {
            assertNotEquals(new UserEntity("Alice", Role.CUSTOMER), new UserEntity("Alice", Role.CUSTOMER));
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            UserEntity user = new UserEntity("Alice", Role.CUSTOMER);
            assertNotEquals(user, "not a user");
            assertNotEquals(null, user);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the name and role")
        void includesKeyFields() {
            String text = new UserEntity("Alice", Role.CUSTOMER).toString();
            assertTrue(text.contains("Alice"));
            assertTrue(text.contains("CUSTOMER"));
        }
    }
}
