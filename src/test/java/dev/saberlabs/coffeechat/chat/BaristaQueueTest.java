package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.BaristaQueue.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BaristaQueue")
class BaristaQueueTest {

    private BaristaQueue queue;

    @BeforeEach
    void setUp() {
        queue = new BaristaQueue();
    }

    @Nested
    @DisplayName("matching")
    class MatchingTests {

        @Test
        @DisplayName("a ready barista then a waiting customer are matched")
        void readyThenWaiting() {
            assertTrue(queue.baristaReady(1).isEmpty());
            assertTrue(queue.isReady(1));

            Optional<Match> match = queue.customerWaiting(100);

            assertEquals(Optional.of(new Match(100, 1)), match);
            assertTrue(queue.isBusy(1));
            assertFalse(queue.isReady(1));
            assertEquals(Optional.of(1L), queue.baristaOf(100));
        }

        @Test
        @DisplayName("a waiting customer then a ready barista are matched")
        void waitingThenReady() {
            assertTrue(queue.customerWaiting(100).isEmpty());
            assertTrue(queue.isWaiting(100));

            assertEquals(Optional.of(new Match(100, 1)), queue.baristaReady(1));
            assertFalse(queue.isWaiting(100));
        }

        @Test
        @DisplayName("the longest-waiting customer is served first")
        void oldestCustomerFirst() {
            queue.customerWaiting(100);
            queue.customerWaiting(101);

            assertEquals(Optional.of(new Match(100, 1)), queue.baristaReady(1));
            assertEquals(Optional.of(new Match(101, 2)), queue.baristaReady(2));
        }

        @Test
        @DisplayName("the longest-ready barista is used first")
        void oldestBaristaFirst() {
            queue.baristaReady(1);
            queue.baristaReady(2);

            assertEquals(Optional.of(new Match(100, 1)), queue.customerWaiting(100));
            assertEquals(Optional.of(new Match(101, 2)), queue.customerWaiting(101));
        }

        @Test
        @DisplayName("counts reflect the queue")
        void counts() {
            queue.baristaReady(1);
            queue.baristaReady(2);
            queue.customerWaiting(100);
            queue.customerWaiting(101);
            queue.customerWaiting(102);

            assertEquals(0, queue.readyCount());
            assertEquals(2, queue.busyCount());
            assertEquals(1, queue.waitingCount());
        }
    }

    @Nested
    @DisplayName("idempotency")
    class IdempotencyTests {

        @Test
        @DisplayName("registering a READY barista twice keeps one entry")
        void readyTwice() {
            queue.baristaReady(1);
            assertTrue(queue.baristaReady(1).isEmpty());

            assertEquals(1, queue.readyCount());
        }

        @Test
        @DisplayName("a BUSY barista who registers again is not queued for a second customer")
        void readyWhileBusy() {
            queue.baristaReady(1);
            queue.customerWaiting(100);

            assertTrue(queue.baristaReady(1).isEmpty());
            assertEquals(0, queue.readyCount());
            assertTrue(queue.customerWaiting(101).isEmpty());
            assertTrue(queue.isWaiting(101));
        }

        @Test
        @DisplayName("a session that is already waiting or matched is not queued twice")
        void waitingTwice() {
            queue.customerWaiting(100);
            assertTrue(queue.customerWaiting(100).isEmpty());
            assertEquals(1, queue.waitingCount());

            queue.baristaReady(1);
            assertTrue(queue.customerWaiting(100).isEmpty());
            assertEquals(0, queue.waitingCount());
        }
    }

    @Nested
    @DisplayName("sessionEnded()")
    class SessionEndedTests {

        @Test
        @DisplayName("frees the barista and rematches them with the next waiting customer")
        void rematches() {
            queue.baristaReady(1);
            queue.customerWaiting(100);
            queue.customerWaiting(101);

            assertEquals(Optional.of(new Match(101, 1)), queue.sessionEnded(100));
            assertEquals(Optional.of(1L), queue.baristaOf(101));
            assertTrue(queue.baristaOf(100).isEmpty());
        }

        @Test
        @DisplayName("with nobody waiting, the barista goes back to READY")
        void backToReady() {
            queue.baristaReady(1);
            queue.customerWaiting(100);

            assertTrue(queue.sessionEnded(100).isEmpty());
            assertTrue(queue.isReady(1));
            assertFalse(queue.isBusy(1));
        }

        @Test
        @DisplayName("ending the same session twice is safe and frees the barista once")
        void doubleEnd() {
            queue.baristaReady(1);
            queue.customerWaiting(100);
            queue.sessionEnded(100);

            assertTrue(queue.sessionEnded(100).isEmpty());
            assertEquals(1, queue.readyCount());
        }

        @Test
        @DisplayName("an unknown session is a no-op")
        void unknown() {
            assertTrue(queue.sessionEnded(999).isEmpty());
            assertEquals(0, queue.readyCount());
        }

        @Test
        @DisplayName("ending a WAITING session removes it from the line: a barista who registers afterwards is NOT matched and stays READY")
        void endingWaitingSessionRemovesIt() {
            queue.customerWaiting(100);

            assertTrue(queue.sessionEnded(100).isEmpty());
            assertFalse(queue.isWaiting(100));
            assertEquals(0, queue.waitingCount());

            assertTrue(queue.baristaReady(1).isEmpty());
            assertTrue(queue.isReady(1));
            assertFalse(queue.isBusy(1));
        }

        @Test
        @DisplayName("ending a WAITING session keeps the others in order")
        void endingWaitingKeepsOthers() {
            queue.customerWaiting(100);
            queue.customerWaiting(101);
            queue.customerWaiting(102);

            queue.sessionEnded(101);

            assertEquals(Optional.of(new Match(100, 1)), queue.baristaReady(1));
            assertEquals(Optional.of(new Match(102, 2)), queue.baristaReady(2));
        }
    }

    @Nested
    @DisplayName("baristaOffline()")
    class OfflineTests {

        @Test
        @DisplayName("removes a READY barista from the pool")
        void readyRemoved() {
            queue.baristaReady(1);
            queue.baristaOffline(1);

            assertFalse(queue.isReady(1));
            assertTrue(queue.customerWaiting(100).isEmpty());
        }

        @Test
        @DisplayName("leaves a BUSY barista's session alone")
        void busyUntouched() {
            queue.baristaReady(1);
            queue.customerWaiting(100);

            queue.baristaOffline(1);

            assertTrue(queue.isBusy(1));
            assertEquals(Optional.of(1L), queue.baristaOf(100));
        }

        @Test
        @DisplayName("a barista who went offline while BUSY is not returned to READY when the session ends")
        void offlineWhileBusyNotReturned() {
            queue.baristaReady(1);
            queue.customerWaiting(100);
            queue.baristaOffline(1);

            assertTrue(queue.sessionEnded(100).isEmpty());
            assertFalse(queue.isReady(1));
            assertFalse(queue.isBusy(1));
        }

        @Test
        @DisplayName("nor rematched with a customer already waiting")
        void offlineWhileBusyNotRematched() {
            queue.baristaReady(1);
            queue.customerWaiting(100);
            queue.customerWaiting(101);
            queue.baristaOffline(1);

            assertTrue(queue.sessionEnded(100).isEmpty());
            assertTrue(queue.isWaiting(101));
        }

        @Test
        @DisplayName("an unknown barista is a no-op, and they can register normally afterwards")
        void unknown() {
            queue.baristaOffline(7);
            queue.baristaReady(7);
            assertTrue(queue.isReady(7));
        }
    }

    @Nested
    @DisplayName("matchFailed()")
    class MatchFailedTests {

        @Test
        @DisplayName("puts the barista at the FRONT of the ready line, ahead of one who registered earlier")
        void baristaToFront() {
            queue.baristaReady(1);
            Match match = queue.customerWaiting(100).orElseThrow();
            queue.baristaReady(2);

            queue.matchFailed(match, false);

            assertEquals(Optional.of(new Match(101, 1)), queue.customerWaiting(101));
        }

        @Test
        @DisplayName("puts a still-waiting session back at the FRONT of the waiting line")
        void sessionStillWaiting() {
            queue.baristaReady(1);
            Match match = queue.customerWaiting(100).orElseThrow();
            queue.customerWaiting(101);

            queue.matchFailed(match, true);

            assertTrue(queue.isWaiting(100));
            assertEquals(Optional.of(new Match(100, 2)), queue.baristaReady(2));
        }

        @Test
        @DisplayName("drops a session that is no longer waiting")
        void sessionGone() {
            queue.baristaReady(1);
            Match match = queue.customerWaiting(100).orElseThrow();

            queue.matchFailed(match, false);

            assertFalse(queue.isWaiting(100));
            assertTrue(queue.isReady(1));
            assertTrue(queue.baristaOf(100).isEmpty());
        }

        @Test
        @DisplayName("a stale match (session already ended) changes nothing")
        void staleMatch() {
            queue.baristaReady(1);
            Match match = queue.customerWaiting(100).orElseThrow();
            queue.sessionEnded(100);
            queue.customerWaiting(101);

            queue.matchFailed(match, true);

            assertFalse(queue.isWaiting(100));
            assertEquals(Optional.of(1L), queue.baristaOf(101));
        }

        @Test
        @DisplayName("a barista who asked to go offline meanwhile is not returned to READY")
        void offlineMeanwhile() {
            queue.baristaReady(1);
            Match match = queue.customerWaiting(100).orElseThrow();
            queue.baristaOffline(1);

            queue.matchFailed(match, true);

            assertFalse(queue.isReady(1));
            assertFalse(queue.isBusy(1));
            assertTrue(queue.isWaiting(100));
        }
    }

    @Nested
    @DisplayName("restoreActiveAssignment()")
    class RestoreTests {

        @Test
        @DisplayName("marks the barista BUSY with that session, and they cannot be matched again")
        void restores() {
            queue.restoreActiveAssignment(100, 1);

            assertTrue(queue.isBusy(1));
            assertEquals(Optional.of(1L), queue.baristaOf(100));
            assertTrue(queue.baristaReady(1).isEmpty());
            assertEquals(0, queue.readyCount());
        }

        @Test
        @DisplayName("takes the barista out of READY and the session out of the waiting line")
        void removesFromLines() {
            queue.baristaReady(1);
            queue.restoreActiveAssignment(100, 1);
            assertEquals(0, queue.readyCount());

            queue.customerWaiting(101);
            queue.restoreActiveAssignment(101, 2);
            assertFalse(queue.isWaiting(101));
        }

        @Test
        @DisplayName("is idempotent, and ending the session then frees the barista")
        void idempotent() {
            queue.restoreActiveAssignment(100, 1);
            queue.restoreActiveAssignment(100, 1);
            assertEquals(1, queue.busyCount());

            queue.sessionEnded(100);
            assertFalse(queue.isBusy(1));
        }
    }

    @Test
    @DisplayName("clear() empties every structure")
    void clearEmpties() {
        queue.baristaReady(1);
        queue.baristaReady(2);
        queue.customerWaiting(100);
        queue.customerWaiting(101);
        queue.customerWaiting(102);
        queue.baristaOffline(2);

        queue.clear();

        assertEquals(0, queue.readyCount());
        assertEquals(0, queue.busyCount());
        assertEquals(0, queue.waitingCount());
        assertTrue(queue.baristaOf(100).isEmpty());
    }

    @Nested
    @DisplayName("concurrency")
    class ConcurrencyTests {

        private static final int PARTIES = 200;

        private <T> List<T> runAll(List<java.util.concurrent.Callable<T>> jobs) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(16);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<T>> futures = new ArrayList<>();
                for (java.util.concurrent.Callable<T> job : jobs) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return job.call();
                    }));
                }
                start.countDown();
                List<T> results = new ArrayList<>();
                for (Future<T> f : futures) {
                    results.add(f.get(20, TimeUnit.SECONDS));
                }
                return results;
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("customers and baristas arriving together yield no duplicate or missing matches")
        void noDuplicateMatches() throws Exception {
            List<java.util.concurrent.Callable<Optional<Match>>> jobs = new ArrayList<>();
            for (int i = 0; i < PARTIES; i++) {
                long id = i;
                jobs.add(() -> queue.baristaReady(1000 + id));
                jobs.add(() -> queue.customerWaiting(id));
            }

            List<Match> matches = runAll(jobs).stream().flatMap(Optional::stream).toList();

            assertEquals(PARTIES, matches.size());
            assertEquals(PARTIES, matches.stream().map(Match::sessionId).distinct().count());
            assertEquals(PARTIES, matches.stream().map(Match::baristaId).distinct().count());
            assertEquals(0, queue.readyCount());
            assertEquals(0, queue.waitingCount());
        }

        @Test
        @DisplayName("ending the same session from many threads frees the barista exactly once")
        void concurrentSessionEnded() throws Exception {
            queue.baristaReady(1);
            queue.customerWaiting(100);
            queue.customerWaiting(101);

            List<java.util.concurrent.Callable<Optional<Match>>> jobs = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                jobs.add(() -> queue.sessionEnded(100));
            }

            List<Match> matches = runAll(jobs).stream().flatMap(Optional::stream).toList();

            assertEquals(List.of(new Match(101, 1)), matches);
            assertEquals(1, queue.busyCount());
            assertEquals(0, queue.readyCount());
        }

        @Test
        @DisplayName("a barista registering from many threads is never assigned two sessions at once")
        void baristaNeverDoubleAssigned() throws Exception {
            for (int i = 0; i < 50; i++) {
                queue.customerWaiting(i);
            }
            List<java.util.concurrent.Callable<Optional<Match>>> jobs = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                jobs.add(() -> queue.baristaReady(1));
            }

            List<Match> matches = runAll(jobs).stream().flatMap(Optional::stream).toList();

            assertEquals(1, matches.size());
            assertEquals(49, queue.waitingCount());
            Set<Long> busy = new HashSet<>(Collections.singleton(1L));
            assertEquals(busy.size(), queue.busyCount());
        }
    }
}
