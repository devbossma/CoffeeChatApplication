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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        @DisplayName("rejects a second ACTIVE session for the same barista (uq_chat_sessions_active_barista)")
        void rejectsSecondActiveSessionForSameBarista() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            UserEntity carl = userRepository.saveAndFlush(new UserEntity("Carl", Role.CUSTOMER));
            sessionRepository.saveAndFlush(new ChatSessionEntity(customer, barista, SessionStatus.ACTIVE, Instant.now()));

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> sessionRepository.saveAndFlush(new ChatSessionEntity(carl, barista, SessionStatus.ACTIVE, Instant.now())));
            assertTrue(thrown.getMostSpecificCause().getMessage().contains("uq_chat_sessions_active_barista"));
        }

        @Test
        @DisplayName("allows a barista a new ACTIVE session once the previous one is INACTIVE")
        void allowsBaristaAgainAfterInactive() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            UserEntity carl = userRepository.saveAndFlush(new UserEntity("Carl", Role.CUSTOMER));
            ChatSessionEntity first = sessionRepository.saveAndFlush(new ChatSessionEntity(customer, barista, SessionStatus.ACTIVE, Instant.now()));
            first.status(SessionStatus.INACTIVE);
            sessionRepository.saveAndFlush(first);

            ChatSessionEntity second = sessionRepository.saveAndFlush(new ChatSessionEntity(carl, barista, SessionStatus.ACTIVE, Instant.now()));

            assertEquals(SessionStatus.ACTIVE, second.status());
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
    @DisplayName("findByStatusOrderByIdAsc()")
    class FindByStatusOrderByIdAscTests {

        @Test
        @DisplayName("returns the sessions in creation order")
        void oldestFirst() {
            UserEntity second = userRepository.saveAndFlush(new UserEntity("Carl", Role.CUSTOMER));
            UserEntity third = userRepository.saveAndFlush(new UserEntity("Dee", Role.CUSTOMER));
            ChatSessionEntity a = sessionRepository.saveAndFlush(new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));
            ChatSessionEntity b = sessionRepository.saveAndFlush(new ChatSessionEntity(second, null, SessionStatus.WAITING, Instant.now()));
            ChatSessionEntity c = sessionRepository.saveAndFlush(new ChatSessionEntity(third, null, SessionStatus.WAITING, Instant.now()));

            List<ChatSessionEntity> waiting = sessionRepository.findByStatusOrderByIdAsc(SessionStatus.WAITING);

            assertEquals(List.of(a.id(), b.id(), c.id()), waiting.stream().map(ChatSessionEntity::id).toList());
        }

        @Test
        @DisplayName("is empty when no session has that status")
        void emptyWhenNone() {
            assertTrue(sessionRepository.findByStatusOrderByIdAsc(SessionStatus.ACTIVE).isEmpty());
        }
    }

    @Nested
    @DisplayName("activateIfWaiting()")
    class ActivateIfWaitingTests {

        @Test
        @DisplayName("activates a WAITING session with the barista and reports 1")
        void activates() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            ChatSessionEntity waiting = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));

            int updated = sessionRepository.activateIfWaiting(waiting.id(), barista);
            entityManager.clear();

            assertEquals(1, updated);
            ChatSessionEntity reloaded = sessionRepository.findById(waiting.id()).orElseThrow();
            assertEquals(SessionStatus.ACTIVE, reloaded.status());
            assertEquals(barista.id(), reloaded.barista().id());
        }

        @Test
        @DisplayName("leaves an INACTIVE session alone and reports 0 (it ended in the meantime)")
        void skipsInactive() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            ChatSessionEntity ended = new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now());
            ended.status(SessionStatus.INACTIVE);
            ended = sessionRepository.saveAndFlush(ended);

            assertEquals(0, sessionRepository.activateIfWaiting(ended.id(), barista));
            entityManager.clear();

            ChatSessionEntity reloaded = sessionRepository.findById(ended.id()).orElseThrow();
            assertEquals(SessionStatus.INACTIVE, reloaded.status());
            assertNull(reloaded.barista());
        }

        @Test
        @DisplayName("reports 0 for a session that is already ACTIVE (never re-assigns a barista)")
        void skipsAlreadyActive() {
            UserEntity first = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            UserEntity second = userRepository.saveAndFlush(new UserEntity("Bea", Role.BARISTA));
            ChatSessionEntity active = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, first, SessionStatus.ACTIVE, Instant.now()));

            assertEquals(0, sessionRepository.activateIfWaiting(active.id(), second));
            entityManager.clear();

            assertEquals(first.id(), sessionRepository.findById(active.id()).orElseThrow().barista().id());
        }

        @Test
        @DisplayName("reports 0 for an unknown session")
        void unknown() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            assertEquals(0, sessionRepository.activateIfWaiting(404L, barista));
        }
    }

    @Nested
    @DisplayName("endIfNotInactive()")
    class EndIfNotInactiveTests {

        @Test
        @DisplayName("ends a WAITING session and reports 1")
        void endsWaiting() {
            ChatSessionEntity waiting = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));

            assertEquals(1, sessionRepository.endIfNotInactive(waiting.id()));
            entityManager.clear();

            assertEquals(SessionStatus.INACTIVE, sessionRepository.findById(waiting.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("ends an ACTIVE session and reports 1")
        void endsActive() {
            UserEntity barista = userRepository.saveAndFlush(new UserEntity("Bob", Role.BARISTA));
            ChatSessionEntity active = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, barista, SessionStatus.ACTIVE, Instant.now()));

            assertEquals(1, sessionRepository.endIfNotInactive(active.id()));
        }

        @Test
        @DisplayName("a second end reports 0, so only one of two concurrent ends gets to free the barista")
        void secondEndIsZero() {
            ChatSessionEntity waiting = sessionRepository.saveAndFlush(
                    new ChatSessionEntity(customer, null, SessionStatus.WAITING, Instant.now()));

            assertEquals(1, sessionRepository.endIfNotInactive(waiting.id()));
            assertEquals(0, sessionRepository.endIfNotInactive(waiting.id()));
        }

        @Test
        @DisplayName("reports 0 for an unknown session")
        void unknown() {
            assertEquals(0, sessionRepository.endIfNotInactive(404L));
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
