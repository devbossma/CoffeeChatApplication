package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
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

@DisplayName("ChatMessageEntity")
class ChatMessageEntityTest {

    private static final Instant NOW = Instant.now();
    private static final UserEntity CUSTOMER = new UserEntity("Alice", Role.CUSTOMER);
    private static final ChatSessionEntity SESSION = new ChatSessionEntity(CUSTOMER, null, SessionStatus.WAITING, NOW);
    private static final OrderEntity ORDER = new OrderEntity(CUSTOMER, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED,
            LoyaltyTier.REGULAR, PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO), NOW, NOW);

    private static void assignId(ChatMessageEntity entity, long id) throws Exception {
        Field field = ChatMessageEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static ChatMessageEntity newMessage() {
        return new ChatMessageEntity(SESSION, MessageType.CHAT_MESSAGE, CUSTOMER, "Alice", "Hello!", NOW, ORDER);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("captures every field for a human message")
        void capturesHumanMessage() {
            ChatMessageEntity message = newMessage();
            assertEquals(SESSION, message.session());
            assertEquals(MessageType.CHAT_MESSAGE, message.type());
            assertEquals(CUSTOMER, message.sender());
            assertEquals("Alice", message.senderName());
            assertEquals("Hello!", message.content());
            assertEquals(NOW, message.sentAt());
            assertEquals(ORDER, message.order());
        }

        @Test
        @DisplayName("allows a null sender and a null order (system message)")
        void allowsSystemMessage() {
            ChatMessageEntity message = new ChatMessageEntity(
                    SESSION, MessageType.SYSTEM_MESSAGE, null, "System", "Order placed!", NOW, null);
            assertNull(message.sender());
            assertNull(message.order());
        }

        @Test
        @DisplayName("rejects a null session")
        void rejectsNullSession() {
            assertThrows(NullPointerException.class, () -> new ChatMessageEntity(
                    null, MessageType.CHAT_MESSAGE, CUSTOMER, "Alice", "hi", NOW, null));
        }

        @Test
        @DisplayName("rejects a null type")
        void rejectsNullType() {
            assertThrows(NullPointerException.class, () -> new ChatMessageEntity(
                    SESSION, null, CUSTOMER, "Alice", "hi", NOW, null));
        }

        @Test
        @DisplayName("rejects a null senderName")
        void rejectsNullSenderName() {
            assertThrows(NullPointerException.class, () -> new ChatMessageEntity(
                    SESSION, MessageType.CHAT_MESSAGE, CUSTOMER, null, "hi", NOW, null));
        }

        @Test
        @DisplayName("rejects a null content")
        void rejectsNullContent() {
            assertThrows(NullPointerException.class, () -> new ChatMessageEntity(
                    SESSION, MessageType.CHAT_MESSAGE, CUSTOMER, "Alice", null, NOW, null));
        }

        @Test
        @DisplayName("rejects a null sentAt")
        void rejectsNullSentAt() {
            assertThrows(NullPointerException.class, () -> new ChatMessageEntity(
                    SESSION, MessageType.CHAT_MESSAGE, CUSTOMER, "Alice", "hi", null, null));
        }
    }

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("an entity equals itself")
        void reflexive() {
            ChatMessageEntity message = newMessage();
            assertEquals(message, message);
        }

        @Test
        @DisplayName("two persisted entities with the same id are equal")
        void sameIdEqual() throws Exception {
            ChatMessageEntity a = newMessage();
            ChatMessageEntity b = newMessage();
            assignId(a, 6L);
            assignId(b, 6L);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("two persisted entities with different ids are not equal")
        void differentIdNotEqual() throws Exception {
            ChatMessageEntity a = newMessage();
            ChatMessageEntity b = newMessage();
            assignId(a, 1L);
            assignId(b, 2L);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two id-less entities are not equal")
        void idlessNotEqual() {
            assertNotEquals(newMessage(), newMessage());
        }

        @Test
        @DisplayName("not equal to a different type or to null")
        void differentTypeOrNullNotEqual() {
            ChatMessageEntity message = newMessage();
            assertNotEquals(message, "not a message");
            assertNotEquals(null, message);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("includes the message type and sender name")
        void includesKeyFields() {
            String text = newMessage().toString();
            assertTrue(text.contains("CHAT_MESSAGE"));
            assertTrue(text.contains("Alice"));
        }
    }
}
