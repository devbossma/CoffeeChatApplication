package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatSessionEntity")
class ChatSessionEntityTest {

    private static final Instant NOW = Instant.now();
    private static final UserEntity CUSTOMER = new UserEntity("Alice", Role.CUSTOMER);
    private static final UserEntity BARISTA = new UserEntity("Bob", Role.BARISTA);

    private static void assignId(ChatSessionEntity entity, long id) throws Exception {
        Field field = ChatSessionEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static ChatSessionEntity newSession() {
        return new ChatSessionEntity(CUSTOMER, null, SessionStatus.WAITING, NOW);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field, allowing a null barista")
        void capturesFields() {
            ChatSessionEntity session = newSession();
            assertEquals(CUSTOMER, session.customer());
            assertNull(session.barista());
            assertEquals(SessionStatus.WAITING, session.status());
            assertEquals(NOW, session.createdAt());
        }

        @Test
        @DisplayName("rejects a null customer")
        void rejectsNullCustomer() {
            assertThrows(NullPointerException.class, () -> new ChatSessionEntity(null, BARISTA, SessionStatus.WAITING, NOW));
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            assertThrows(NullPointerException.class, () -> new ChatSessionEntity(CUSTOMER, BARISTA, null, NOW));
        }

        @Test
        @DisplayName("rejects a null createdAt")
        void rejectsNullCreatedAt() {
            assertThrows(NullPointerException.class, () -> new ChatSessionEntity(CUSTOMER, BARISTA, SessionStatus.WAITING, null));
        }
    }

    @Nested
    @DisplayName("barista(UserEntity)")
    class BaristaSetterTests {

        @Test
        @DisplayName("assigns a barista to a waiting session")
        void assignsBarista() {
            ChatSessionEntity session = newSession();
            session.barista(BARISTA);
            assertEquals(BARISTA, session.barista());
        }

        @Test
        @DisplayName("allows clearing the barista back to null")
        void allowsClearingBarista() {
            ChatSessionEntity session = new ChatSessionEntity(CUSTOMER, BARISTA, SessionStatus.ACTIVE, NOW);
            session.barista(null);
            assertNull(session.barista());
        }
    }

    @Nested
    @DisplayName("status(SessionStatus)")
    class StatusSetterTests {

        @Test
        @DisplayName("updates the status")
        void updatesStatus() {
            ChatSessionEntity session = newSession();
            session.status(SessionStatus.ACTIVE);
            assertEquals(SessionStatus.ACTIVE, session.status());
        }

        @Test
        @DisplayName("rejects a null status")
        void rejectsNullStatus() {
            ChatSessionEntity session = newSession();
            assertThrows(NullPointerException.class, () -> session.status(null));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            ChatSessionEntity session = newSession();
            assertEquals(session, session);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            ChatSessionEntity a = newSession();
            ChatSessionEntity b = newSession();
            assignId(a, 4L);
            assignId(b, 4L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            ChatSessionEntity a = newSession();
            ChatSessionEntity b = newSession();
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal")
        void idlessNotEqual() {
            assertNotEquals(newSession(), newSession());
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            ChatSessionEntity session = newSession();
            assertNotEquals(session, "not a session");
            assertNotEquals(null, session);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("shows a null barista as null")
        void showsNullBarista() {
            assertTrue(newSession().toString().contains("barista=null"));
        }

        @Test
        @DisplayName("includes the barista's id once assigned")
        void includesBaristaId() throws Exception {
            UserEntity barista = new UserEntity("Charlie", Role.BARISTA);
            Field field = UserEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(barista, 9L);
            ChatSessionEntity session = new ChatSessionEntity(CUSTOMER, barista, SessionStatus.ACTIVE, NOW);
            assertTrue(session.toString().contains("barista=9"));
        }
    }
}
