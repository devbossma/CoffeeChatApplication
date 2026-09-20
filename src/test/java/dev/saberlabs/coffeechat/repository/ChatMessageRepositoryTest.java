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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    """, NOT_NULL_VIOLATION, "session_id");
        }

        @Test
        @DisplayName("rejects blank content at the database level")
        void rejectsBlankContent() {
            // A fresh customer+session is inserted here, not the @BeforeEach fixture: that
            // fixture lives in this test's own uncommitted JPA transaction and is invisible to
            // assertConstraintViolation's separate connection, which would otherwise fail on the
            // session_id FK instead of the content CHECK this test names.
            assertConstraintViolation(
                    List.of(
                            "INSERT INTO user_accounts (id, name, role) VALUES (900003, 'SetupCustomer', 'CUSTOMER')",
                            "INSERT INTO chat_sessions (id, customer_id, status, created_at) VALUES (900003, 900003, 'WAITING', now())"),
                    """
                    INSERT INTO chat_messages (session_id, type, sender_name, content, sent_at)
                    VALUES (900003, 'CHAT_MESSAGE', 'Alice', '   ', now())
                    """,
                    CHECK_VIOLATION, "chk_chat_message_content_not_blank");
        }

        @Test
        @DisplayName("rejects content over 2000 characters at the database level")
        void rejectsOverlongContent() {
            String tooLong = "x".repeat(2001);
            assertConstraintViolation(
                    List.of(
                            "INSERT INTO user_accounts (id, name, role) VALUES (900004, 'SetupCustomer', 'CUSTOMER')",
                            "INSERT INTO chat_sessions (id, customer_id, status, created_at) VALUES (900004, 900004, 'WAITING', now())"),
                    """
                    INSERT INTO chat_messages (session_id, type, sender_name, content, sent_at)
                    VALUES (900004, 'CHAT_MESSAGE', 'Alice', '%s', now())
                    """.formatted(tooLong),
                    CHECK_VIOLATION, "chk_chat_message_content_length");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @Test
        @DisplayName("does not dereference an uninitialized lazy session proxy on a detached entity")
        void safeOnDetachedLazyProxy() {
            ChatMessageEntity saved = messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "Hello!", Instant.now(), null));
            entityManager.clear();

            ChatMessageEntity reloaded = messageRepository.findById(saved.id()).orElseThrow();
            entityManager.detach(reloaded);

            String text = assertDoesNotThrow(reloaded::toString);
            assertTrue(text.contains("<lazy>"));
        }
    }

    @Nested
    @DisplayName("findBySessionIdOrderBySentAtAscIdAsc()")
    class FindBySessionIdOrderBySentAtAscTests {

        @Test
        @DisplayName("returns the session's messages oldest first")
        void returnsOldestFirst() {
            Instant base = Instant.now();
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "first", base, null));
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.SYSTEM_MESSAGE, null, "System", "second", base.plusSeconds(1), null));
            messageRepository.saveAndFlush(new ChatMessageEntity(
                    session, MessageType.CHAT_MESSAGE, customer, "Alice", "third", base.plusSeconds(2), null));

            List<ChatMessageEntity> history = messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id());

            assertEquals(List.of("first", "second", "third"), history.stream().map(ChatMessageEntity::content).toList());
        }

        @Test
        @DisplayName("messages written at the very same instant keep their write order (id breaks the tie)")
        void sameInstantKeepsWriteOrder() {
            Instant same = Instant.now();
            messageRepository.saveAndFlush(new ChatMessageEntity(session, MessageType.CHAT_MESSAGE, customer, "Alice", "one", same, null));
            messageRepository.saveAndFlush(new ChatMessageEntity(session, MessageType.CHAT_MESSAGE, customer, "Alice", "two", same, null));
            messageRepository.saveAndFlush(new ChatMessageEntity(session, MessageType.SYSTEM_MESSAGE, null, "System", "three", same, null));

            List<ChatMessageEntity> history = messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id());

            assertEquals(List.of("one", "two", "three"), history.stream().map(ChatMessageEntity::content).toList());
        }

        @Test
        @DisplayName("returns only that session's messages")
        void onlyThatSession() {
            UserEntity carl = userRepository.saveAndFlush(new UserEntity("Carl", Role.CUSTOMER));
            ChatSessionEntity other = sessionRepository.saveAndFlush(new ChatSessionEntity(carl, null, SessionStatus.WAITING, Instant.now()));
            messageRepository.saveAndFlush(new ChatMessageEntity(session, MessageType.CHAT_MESSAGE, customer, "Alice", "mine", Instant.now(), null));
            messageRepository.saveAndFlush(new ChatMessageEntity(other, MessageType.CHAT_MESSAGE, carl, "Carl", "theirs", Instant.now(), null));

            assertEquals(List.of("mine"), messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id()).stream().map(ChatMessageEntity::content).toList());
            assertEquals(List.of("theirs"), messageRepository.findBySessionIdOrderBySentAtAscIdAsc(other.id()).stream().map(ChatMessageEntity::content).toList());
        }
    }

    @Nested
    @DisplayName("findBySessionIdOrderBySentAtAscIdAsc(pageable)")
    class PagedTests {

        @Test
        @DisplayName("returns consecutive slices of the same ordering, and an empty page past the end")
        void slices() {
            Instant base = Instant.now();
            for (int i = 0; i < 5; i++) {
                messageRepository.saveAndFlush(new ChatMessageEntity(
                        session, MessageType.CHAT_MESSAGE, customer, "Alice", "m" + i, base.plusSeconds(i), null));
            }

            List<String> first = messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id(), org.springframework.data.domain.PageRequest.of(0, 2))
                    .stream().map(ChatMessageEntity::content).toList();
            List<String> third = messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id(), org.springframework.data.domain.PageRequest.of(2, 2))
                    .stream().map(ChatMessageEntity::content).toList();

            assertEquals(List.of("m0", "m1"), first);
            assertEquals(List.of("m4"), third);
            assertTrue(messageRepository.findBySessionIdOrderBySentAtAscIdAsc(session.id(), org.springframework.data.domain.PageRequest.of(3, 2)).isEmpty());
        }
    }
}
