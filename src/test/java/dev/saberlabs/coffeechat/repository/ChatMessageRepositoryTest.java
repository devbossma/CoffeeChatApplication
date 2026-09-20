package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatMessageEntity;
import dev.saberlabs.coffeechat.entity.ChatSessionEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ChatMessageRepository")
class ChatMessageRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private ChatSessionEntity session;
    private UserEntity customer;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();

        customer = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
        session = sessionRepository.saveAndFlush(new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves and reloads a human chat message")
        void savesAndReloadsChatMessage() {
            ChatMessageEntity saved = messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "Hello!", Instant.now(), null));
            entityManager.clear();

            ChatMessageEntity reloaded = messageRepository.findById(saved.id()).orElseThrow();
            assertEquals("Hello!", reloaded.content());
            assertEquals(customer.id(), reloaded.sender().id());
        }

        @Test
        @DisplayName("saves a system message with a null sender")
        void savesSystemMessageWithNullSender() {
            ChatMessageEntity saved = messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.SYSTEM_MESSAGE, null, "System", "Order placed!", Instant.now(), null));
            entityManager.clear();

            ChatMessageEntity reloaded = messageRepository.findById(saved.id()).orElseThrow();
            assertNull(reloaded.sender());
            assertEquals("System", reloaded.senderName());
        }

        @Test
        @DisplayName("rejects a null session_id at the database level")
        void rejectsNullSession() {
            assertConstraintViolation("""
                    INSERT INTO chat_messages (session_id, type, sender_name, content, sent_at)
                    VALUES (NULL, 'CHAT_MESSAGE', 'Alice', 'hi', now())
                    """);
        }

        @Test
        @DisplayName("rejects blank content at the database level")
        void rejectsBlankContent() {
            assertConstraintViolation("""
                    INSERT INTO chat_messages (session_id, type, sender_name, content, sent_at)
                    VALUES (%d, 'CHAT_MESSAGE', 'Alice', '   ', now())
                    """.formatted(session.id()));
        }

        @Test
        @DisplayName("rejects content over 2000 characters at the database level")
        void rejectsOverlongContent() {
            String tooLong = "x".repeat(2001);
            assertConstraintViolation("""
                    INSERT INTO chat_messages (session_id, type, sender_name, content, sent_at)
                    VALUES (%d, 'CHAT_MESSAGE', 'Alice', '%s', now())
                    """.formatted(session.id(), tooLong));
        }
    }

    @Nested
    @DisplayName("findBySessionIdOrderBySentAtAsc()")
    class FindBySessionIdOrderBySentAtAscTests {

        @Test
        @DisplayName("returns the session's messages oldest first")
        void returnsOldestFirst() throws InterruptedException {
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "first", Instant.now(), null));
            Thread.sleep(5);
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.SYSTEM_MESSAGE, null, "System", "second", Instant.now(), null));
            Thread.sleep(5);
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "third", Instant.now(), null));

            List<ChatMessageEntity> history = messageRepository.findBySessionIdOrderBySentAtAsc(session.id());

            assertEquals(List.of("first", "second", "third"), history.stream().map(ChatMessageEntity::content).toList());
        }
    }
}
