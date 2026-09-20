package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.SessionStatus;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chat matching (store + queue + matchmaker, real database)")
class ChatMatchmakerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ChatMatchmaker matchmaker;
    @Autowired private ChatSessionStore store;
    @Autowired private ChatRecovery recovery;

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private List<String> texts(long sessionId) {
        return store.history(sessionId).stream().map(MessageView::content).toList();
    }

    private <T> List<T> race(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return job.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Nested
    @DisplayName("open()")
    class OpenTests {

        @Test
        @DisplayName("with no barista ready the session is WAITING and queued")
        void waiting() {
            UserEntity alice = customer("Alice");

            SessionView view = matchmaker.open(alice.id());

            assertEquals(SessionStatus.WAITING, view.status());
            assertNull(view.baristaId());
            assertTrue(baristaQueue.isWaiting(view.id()));
            assertEquals(List.of(), texts(view.id()));
        }

        @Test
        @DisplayName("with a barista ready the session is ACTIVE at once, with one 'joined' system message")
        void matchedAtOnce() {
            UserEntity alice = customer("Alice");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());

            SessionView view = matchmaker.open(alice.id());

            assertEquals(SessionStatus.ACTIVE, view.status());
            assertEquals(bob.id(), view.baristaId());
            List<MessageView> history = store.history(view.id());
            assertEquals(1, history.size());
            assertEquals(MessageType.SYSTEM_MESSAGE, history.get(0).type());
            assertNull(history.get(0).senderId());
            assertEquals("Bob joined the chat", history.get(0).content());
        }

        @Test
        @DisplayName("an unknown customer is CustomerNotFound and creates nothing")
        void unknownCustomer() {
            assertThrows(CustomerNotFoundException.class, () -> matchmaker.open(987654L));
            assertEquals(0, count("SELECT count(*) FROM chat_sessions"));
        }

        @Test
        @DisplayName("a second open for the same customer is refused with the existing session's id")
        void alreadyOpen() {
            UserEntity alice = customer("Alice");
            SessionView first = matchmaker.open(alice.id());

            ChatSessionAlreadyOpenException e = assertThrows(ChatSessionAlreadyOpenException.class, () -> matchmaker.open(alice.id()));

            assertEquals(Long.valueOf(first.id()), e.existingSessionId());
            assertEquals(1, count("SELECT count(*) FROM chat_sessions"));
        }

        @Test
        @DisplayName("after the session ends, the same customer may open a new one")
        void reopenAfterEnd() {
            UserEntity alice = customer("Alice");
            SessionView first = matchmaker.open(alice.id());
            matchmaker.end(first.id());

            SessionView second = matchmaker.open(alice.id());

            assertTrue(second.id() != first.id());
            assertEquals(SessionStatus.WAITING, second.status());
        }

        @Test
        @DisplayName("concurrent opens for one customer: exactly one row, exactly one 409")
        void concurrentSameCustomer() throws Exception {
            UserEntity alice = customer("Alice");
            List<Callable<Object>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                jobs.add(() -> {
                    try {
                        return matchmaker.open(alice.id());
                    } catch (ChatSessionAlreadyOpenException e) {
                        return e;
                    }
                });
            }

            List<Object> results = race(jobs);

            assertEquals(1, results.stream().filter(SessionView.class::isInstance).count());
            assertEquals(5, results.stream().filter(ChatSessionAlreadyOpenException.class::isInstance).count());
            assertEquals(1, count("SELECT count(*) FROM chat_sessions"));
            long id = ((SessionView) results.stream().filter(SessionView.class::isInstance).findFirst().orElseThrow()).id();
            results.stream().filter(ChatSessionAlreadyOpenException.class::isInstance)
                    .forEach(e -> assertEquals(Long.valueOf(id), ((ChatSessionAlreadyOpenException) e).existingSessionId()));
        }
    }

    @Nested
    @DisplayName("baristaReady()")
    class BaristaReadyTests {

        @Test
        @DisplayName("matches the longest-waiting customer")
        void matchesOldest() {
            UserEntity alice = customer("Alice");
            UserEntity carl = customer("Carl");
            UserEntity bob = barista("Bob");
            SessionView first = matchmaker.open(alice.id());
            SessionView second = matchmaker.open(carl.id());

            matchmaker.baristaReady(bob.id());

            assertEquals(SessionStatus.ACTIVE, store.find(first.id()).orElseThrow().status());
            assertEquals(SessionStatus.WAITING, store.find(second.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("registering twice does not create a second match or a second message")
        void twiceIsIdempotent() {
            UserEntity alice = customer("Alice");
            UserEntity carl = customer("Carl");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());
            matchmaker.baristaReady(bob.id());
            matchmaker.open(alice.id());

            SessionView second = matchmaker.open(carl.id());

            assertEquals(SessionStatus.WAITING, second.status());
            assertEquals(1, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(1, count("SELECT count(*) FROM chat_messages"));
        }
    }

    @Nested
    @DisplayName("end()")
    class EndTests {

        @Test
        @DisplayName("ends an ACTIVE session, writes one 'ended' message, and rematches the barista with the next customer")
        void endsAndRematches() {
            UserEntity alice = customer("Alice");
            UserEntity carl = customer("Carl");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());
            SessionView first = matchmaker.open(alice.id());
            SessionView second = matchmaker.open(carl.id());

            assertTrue(matchmaker.end(first.id()));

            assertEquals(SessionStatus.INACTIVE, store.find(first.id()).orElseThrow().status());
            assertEquals(List.of("Bob joined the chat", "The chat has ended"), texts(first.id()));
            SessionView rematched = store.find(second.id()).orElseThrow();
            assertEquals(SessionStatus.ACTIVE, rematched.status());
            assertEquals(bob.id(), rematched.baristaId());
        }

        @Test
        @DisplayName("with nobody waiting, the barista becomes READY again")
        void baristaFreed() {
            UserEntity alice = customer("Alice");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());
            SessionView view = matchmaker.open(alice.id());

            matchmaker.end(view.id());

            assertTrue(baristaQueue.isReady(bob.id()));
        }

        @Test
        @DisplayName("ending twice is safe: second call returns false and adds no message")
        void idempotent() {
            UserEntity alice = customer("Alice");
            SessionView view = matchmaker.open(alice.id());

            assertTrue(matchmaker.end(view.id()));
            assertFalse(matchmaker.end(view.id()));

            assertEquals(List.of("The chat has ended"), texts(view.id()));
        }

        @Test
        @DisplayName("an unknown session is a quiet false")
        void unknown() {
            assertFalse(matchmaker.end(424242L));
        }

        @Test
        @DisplayName("ending a WAITING session removes it from the line: a barista registered afterwards is NOT matched and stays READY")
        void endingWaitingSession() {
            UserEntity alice = customer("Alice");
            UserEntity bob = barista("Bob");
            SessionView view = matchmaker.open(alice.id());

            assertTrue(matchmaker.end(view.id()));
            matchmaker.baristaReady(bob.id());

            assertEquals(SessionStatus.INACTIVE, store.find(view.id()).orElseThrow().status());
            assertNull(store.find(view.id()).orElseThrow().baristaId());
            assertTrue(baristaQueue.isReady(bob.id()));
            assertEquals(0, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
        }

        @Test
        @DisplayName("concurrent double end frees the barista exactly once and writes exactly one 'ended' message")
        void concurrentDoubleEnd() throws Exception {
            UserEntity alice = customer("Alice");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());
            SessionView view = matchmaker.open(alice.id());
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                jobs.add(() -> matchmaker.end(view.id()));
            }

            List<Boolean> results = race(jobs);

            assertEquals(1, results.stream().filter(b -> b).count());
            assertEquals(1, baristaQueue.readyCount());
            assertEquals(List.of("Bob joined the chat", "The chat has ended"), texts(view.id()));
        }
    }

    @Nested
    @DisplayName("matching under concurrency")
    class ConcurrentMatchingTests {

        @Test
        @DisplayName("two baristas and two customers arriving together: each session matched once, distinct baristas")
        void twoByTwo() throws Exception {
            UserEntity a = customer("A");
            UserEntity b = customer("B");
            UserEntity x = barista("X");
            UserEntity y = barista("Y");
            List<Callable<Object>> jobs = List.of(
                    () -> matchmaker.open(a.id()),
                    () -> matchmaker.open(b.id()),
                    () -> {
                        matchmaker.baristaReady(x.id());
                        return null;
                    },
                    () -> {
                        matchmaker.baristaReady(y.id());
                        return null;
                    });

            race(jobs);

            assertEquals(2, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(2, count("SELECT count(DISTINCT barista_id) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(2, count("SELECT count(*) FROM chat_messages WHERE content LIKE '% joined the chat'"));
            assertEquals(0, baristaQueue.waitingCount());
            assertEquals(0, baristaQueue.readyCount());
        }

        @Test
        @DisplayName("a barista registered from many threads at once gets exactly one session")
        void baristaRegisteredTwiceConcurrently() throws Exception {
            UserEntity bob = barista("Bob");
            List<UserEntity> customers = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                customers.add(customer("C" + i));
            }
            for (UserEntity c : customers) {
                matchmaker.open(c.id());
            }
            List<Callable<Object>> jobs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                jobs.add(() -> {
                    matchmaker.baristaReady(bob.id());
                    return null;
                });
            }

            race(jobs);

            assertEquals(1, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(1, count("SELECT count(*) FROM chat_messages"));
        }

        @Test
        @DisplayName("ending a session racing its own match never leaves an ACTIVE session or a busy barista behind")
        void endRacingMatch() throws Exception {
            for (int round = 0; round < 5; round++) {
                baristaQueue.clear();
                jdbc.update("DELETE FROM chat_messages");
                jdbc.update("DELETE FROM chat_sessions");
                UserEntity c = customer("Racer" + round);
                UserEntity bob = barista("Bob" + round);
                SessionView view = matchmaker.open(c.id());
                List<Callable<Object>> jobs = List.of(
                        () -> {
                            matchmaker.baristaReady(bob.id());
                            return null;
                        },
                        () -> matchmaker.end(view.id()));

                race(jobs);

                assertEquals(SessionStatus.INACTIVE, store.find(view.id()).orElseThrow().status());
                assertFalse(baristaQueue.isBusy(bob.id()), "round " + round);
                assertTrue(baristaQueue.isReady(bob.id()), "round " + round);
                assertEquals(0, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
            }
        }
    }

    @Nested
    @DisplayName("compensation")
    class CompensationTests {

        @Test
        @DisplayName("a match to a session that ended meanwhile is abandoned; the barista moves on to the next waiting customer")
        void deadMatchRematches() {
            UserEntity alice = customer("Alice");
            UserEntity carl = customer("Carl");
            UserEntity bob = barista("Bob");
            SessionView dead = matchmaker.open(alice.id());
            SessionView live = matchmaker.open(carl.id());
            // The session ends in the database only (as if between the queue decision and the write).
            store.end(dead.id());

            matchmaker.baristaReady(bob.id());

            assertEquals(SessionStatus.INACTIVE, store.find(dead.id()).orElseThrow().status());
            SessionView rematched = store.find(live.id()).orElseThrow();
            assertEquals(SessionStatus.ACTIVE, rematched.status());
            assertEquals(bob.id(), rematched.baristaId());
        }

        @Test
        @DisplayName("a dead match with nobody else waiting puts the barista back in the ready line")
        void deadMatchBaristaReady() {
            UserEntity alice = customer("Alice");
            UserEntity bob = barista("Bob");
            SessionView dead = matchmaker.open(alice.id());
            store.end(dead.id());

            matchmaker.baristaReady(bob.id());

            assertTrue(baristaQueue.isReady(bob.id()));
            assertFalse(baristaQueue.isBusy(bob.id()));
        }

        @Test
        @DisplayName("a failed write (unknown barista id) undoes the match: session waits again, no rows change")
        void failedWriteCompensates() {
            UserEntity alice = customer("Alice");
            SessionView waiting = matchmaker.open(alice.id());

            RuntimeException e = assertThrows(RuntimeException.class, () -> matchmaker.baristaReady(999_999L));

            assertInstanceOf(IllegalStateException.class, e);
            assertTrue(baristaQueue.isWaiting(waiting.id()));
            assertEquals(SessionStatus.WAITING, store.find(waiting.id()).orElseThrow().status());
            assertEquals(0, count("SELECT count(*) FROM chat_messages"));
        }
    }

    @Nested
    @DisplayName("messages")
    class MessageTests {

        @Test
        @DisplayName("history is ordered by sent_at then id, and carries sender ids and order links")
        void ordered() {
            UserEntity alice = customer("Alice");
            SessionView view = matchmaker.open(alice.id());

            for (int i = 0; i < 20; i++) {
                store.addMessage(view.id(), MessageType.CHAT_MESSAGE, alice.id(), "Alice", "m" + i, null);
            }

            List<String> expected = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                expected.add("m" + i);
            }
            assertEquals(expected, texts(view.id()));
            assertEquals(alice.id(), store.history(view.id()).get(0).senderId());
        }

        @Test
        @DisplayName("a message for an unknown session is rejected")
        void unknownSession() {
            assertThrows(IllegalStateException.class,
                    () -> store.addMessage(31337L, MessageType.SYSTEM_MESSAGE, null, "System", "hi", null));
        }

        @Test
        @DisplayName("the database CHECK is the backstop for blank content")
        void blankRejectedByDatabase() {
            UserEntity alice = customer("Alice");
            SessionView view = matchmaker.open(alice.id());

            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> store.addMessage(view.id(), MessageType.CHAT_MESSAGE, alice.id(), "Alice", "   ", null));
        }
    }

    @Nested
    @DisplayName("ChatRecovery.recover()")
    class RecoveryTests {

        @Test
        @DisplayName("restores WAITING sessions oldest first and ACTIVE ones as BUSY, and is idempotent")
        void restores() {
            UserEntity alice = customer("Alice");
            UserEntity carl = customer("Carl");
            UserEntity dee = customer("Dee");
            UserEntity bob = barista("Bob");
            matchmaker.baristaReady(bob.id());
            SessionView active = matchmaker.open(alice.id());
            SessionView w1 = matchmaker.open(carl.id());
            SessionView w2 = matchmaker.open(dee.id());
            baristaQueue.clear();

            assertEquals(3, recovery.recover());
            assertEquals(3, recovery.recover());

            assertTrue(baristaQueue.isBusy(bob.id()));
            assertEquals(2, baristaQueue.waitingCount());
            assertTrue(baristaQueue.isWaiting(w1.id()));
            assertTrue(baristaQueue.isWaiting(w2.id()));

            UserEntity eve = barista("Eve");
            matchmaker.baristaReady(eve.id());
            assertEquals(SessionStatus.ACTIVE, store.find(w1.id()).orElseThrow().status(), "oldest waiting first");
            assertEquals(SessionStatus.WAITING, store.find(w2.id()).orElseThrow().status());
            assertEquals(bob.id(), store.find(active.id()).orElseThrow().baristaId());
        }

        @Test
        @DisplayName("nothing to recover is a zero")
        void nothing() {
            assertEquals(0, recovery.recover());
        }
    }
}
