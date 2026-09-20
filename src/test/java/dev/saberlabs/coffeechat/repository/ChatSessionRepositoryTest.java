package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatSessionEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatSessionRepository")
class ChatSessionRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UserEntity customer;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        customer = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves and reloads a WAITING session with no barista yet")
        void savesAndReloads() {
            ChatSessionEntity saved = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
            entityManager.clear();

            ChatSessionEntity reloaded = sessionRepository.findById(saved.id()).orElseThrow();
            assertEquals(SessionStatus.WAITING, reloaded.status());
        }

        @Test
        @DisplayName("rejects a second non-INACTIVE session for the same customer (partial unique index)")
        void rejectsSecondActiveSessionForSameCustomer() {
            sessionRepository.saveAndFlush(new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> sessionRepository.saveAndFlush(
                            new ChatSessionEntity(customer, null, SessionStatus.ACTIVE, Instant.now())));
            assertTrue(thrown.getMostSpecificCause().getMessage().contains("uq_chat_sessions_active_customer"));
        }

        @Test
        @DisplayName("allows a second session once the first is INACTIVE")
        void allowsSecondSessionOnceFirstInactive() {
            ChatSessionEntity first = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
            first.status(SessionStatus.INACTIVE);
            sessionRepository.saveAndFlush(first);

            ChatSessionEntity second = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));

            assertEquals(2, sessionRepository.count());
            assertEquals(SessionStatus.WAITING, second.status());
        }
    }

    @Nested
    @DisplayName("findByStatus()")
    class FindByStatusTests {

        @Test
        @DisplayName("returns only sessions in the requested status (serves restart recovery)")
        void returnsMatchingSessions() {
            sessionRepository.saveAndFlush(new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
            UserEntity other = userRepository.saveAndFlush(new UserEntity("Carl", Role.CUSTOMER));
            ChatSessionEntity inactive = new ChatSessionEntity(other, null, SessionStatus.WAITING, Instant.now());
            inactive.status(SessionStatus.INACTIVE);
            sessionRepository.saveAndFlush(inactive);

            List<ChatSessionEntity> waiting = sessionRepository.findByStatus(SessionStatus.WAITING);

            assertEquals(1, waiting.size());
            assertEquals(customer.id(), waiting.get(0).customer().id());
        }
    }

    @Nested
    @DisplayName("findByCustomerIdAndStatusNot()")
    class FindByCustomerIdAndStatusNotTests {

        @Test
        @DisplayName("finds the customer's current non-INACTIVE session")
        void findsActiveSession() {
            ChatSessionEntity saved = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.ACTIVE, Instant.now()));

            assertTrue(sessionRepository.findByCustomerIdAndStatusNot(customer.id(), SessionStatus.INACTIVE)
                    .filter(s -> s.id().equals(saved.id()))
                    .isPresent());
        }

        @Test
        @DisplayName("returns empty once the customer's only session is INACTIVE")
        void emptyWhenOnlyInactive() {
            ChatSessionEntity saved = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
            saved.status(SessionStatus.INACTIVE);
            sessionRepository.saveAndFlush(saved);

            assertTrue(sessionRepository.findByCustomerIdAndStatusNot(customer.id(), SessionStatus.INACTIVE).isEmpty());
        }
    }
}
