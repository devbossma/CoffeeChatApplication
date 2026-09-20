package dev.saberlabs.coffeechat.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Actor")
class ActorTest {

    @Nested
    @DisplayName("SYSTEM")
    class SystemTests {

        @Test
        @DisplayName("is an actor with no user id")
        void hasNoUser() {
            assertTrue(Actor.SYSTEM.isSystem());
            assertNull(Actor.SYSTEM.userId());
        }
    }

    @Nested
    @DisplayName("user()")
    class UserTests {

        @Test
        @DisplayName("wraps a user id and is not the system")
        void wraps() {
            Actor actor = Actor.user(7L);
            assertEquals(7L, actor.userId());
            assertFalse(actor.isSystem());
        }

        @Test
        @DisplayName("rejects a null id (a missing identity must never silently become the system)")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> Actor.user(null));
        }

        @Test
        @DisplayName("two actors for the same user are equal, and differ from the system")
        void equality() {
            assertEquals(Actor.user(7L), Actor.user(7L));
            assertNotEquals(Actor.user(7L), Actor.SYSTEM);
        }
    }
}
