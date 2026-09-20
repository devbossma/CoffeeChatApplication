package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.BaristaQueue.Match;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The failure handling of the matching protocol, with the store faked so failures can be injected. */
@DisplayName("ChatMatchmaker (failure handling, faked store)")
class ChatMatchmakerTest {

    private BaristaQueue queue;
    private ChatSessionStore store;
    private ChatMatchmaker matchmaker;

    @BeforeEach
    void setUp() {
        queue = new BaristaQueue();
        store = mock(ChatSessionStore.class);
        matchmaker = new ChatMatchmaker(queue, store);
    }

    private void sessionsExist(long... ids) {
        for (long id : ids) {
            SessionView view = new SessionView(id, id + 1000, null, SessionStatus.WAITING, Instant.now());
            when(store.createWaiting(id + 1000)).thenReturn(view);
            when(store.find(id)).thenReturn(Optional.of(view));
        }
    }

    @Nested
    @DisplayName("a transient write failure")
    class TransientFailureTests {

        @Test
        @DisplayName("does not fail open(): the WAITING session is returned and the pair is left in place")
        void openDoesNotThrow() {
            sessionsExist(1);
            queue.baristaReady(7);
            when(store.activate(1, 7)).thenThrow(new IllegalStateException("db down"));

            SessionView view = matchmaker.open(1001);

            assertEquals(1, view.id());
            assertTrue(queue.isWaiting(1));
            assertTrue(queue.isReady(7));
        }

        @Test
        @DisplayName("is retried by the next operation, and a newcomer does not jump ahead of the older pair")
        void retriedOldestFirst() {
            sessionsExist(1, 2);
            queue.baristaReady(7);
            when(store.activate(1, 7)).thenThrow(new IllegalStateException("db down")).thenReturn(true);
            matchmaker.open(1001);

            matchmaker.open(1002);

            InOrder order = inOrder(store);
            order.verify(store, times(2)).activate(1, 7);
            verify(store, never()).activate(2, 7);
            assertTrue(queue.isWaiting(2));
            assertEquals(java.util.Optional.of(7L), queue.baristaOf(1));
        }

        @Test
        @DisplayName("baristaReady() and end() also retry a pending pair first")
        void otherOperationsHeal() {
            sessionsExist(1);
            queue.baristaReady(7);
            when(store.activate(1, 7)).thenThrow(new IllegalStateException("db down")).thenReturn(true);
            matchmaker.open(1001);

            matchmaker.baristaReady(8);

            verify(store, times(2)).activate(1, 7);
            assertTrue(queue.isReady(8));
        }
    }

    @Nested
    @DisplayName("a permanently unusable barista")
    class PermanentFailureTests {

        @Test
        @DisplayName("is dropped from the queue, the customer keeps waiting at the front, and the next barista takes them")
        void baristaDropped() {
            sessionsExist(1);
            when(store.activate(1, 9)).thenThrow(new ChatBaristaUnavailableException(9, "no such user"));
            when(store.activate(1, 7)).thenReturn(true);
            queue.customerWaiting(1);

            matchmaker.baristaReady(9);

            assertFalse(queue.isReady(9));
            assertFalse(queue.isBusy(9));
            assertTrue(queue.isWaiting(1));

            matchmaker.baristaReady(7);
            verify(store).activate(1, 7);
            assertTrue(queue.isBusy(7));
        }

        @Test
        @DisplayName("is not offered again: a later open() does not loop on the same barista")
        void notOfferedAgain() {
            sessionsExist(1, 2);
            when(store.activate(anyLong(), org.mockito.ArgumentMatchers.eq(9L)))
                    .thenThrow(new ChatBaristaUnavailableException(9, "no such user"));
            matchmaker.baristaReady(9);

            matchmaker.open(1001);
            matchmaker.open(1002);

            verify(store, times(1)).activate(anyLong(), org.mockito.ArgumentMatchers.eq(9L));
        }

        @Test
        @DisplayName("when a barista is dropped while another is ready, the customer is paired with the other in the same call")
        void nextBaristaTakesOver() {
            sessionsExist(1);
            when(store.activate(1, 9)).thenThrow(new ChatBaristaUnavailableException(9, "no such user"));
            when(store.activate(1, 7)).thenReturn(true);
            queue.baristaReady(9);
            queue.baristaReady(7);

            matchmaker.open(1001);

            verify(store).activate(1, 9);
            verify(store).activate(1, 7);
            assertEquals(java.util.Optional.of(7L), queue.baristaOf(1));
        }
    }

    @Nested
    @DisplayName("a session that ended before its match was written")
    class DeadSessionTests {

        @Test
        @DisplayName("frees the barista and pairs them with the next waiting customer in the same call")
        void rematches() {
            sessionsExist(1, 2);
            when(store.activate(1, 7)).thenReturn(false);
            when(store.activate(2, 7)).thenReturn(true);
            queue.customerWaiting(1);
            queue.customerWaiting(2);

            matchmaker.baristaReady(7);

            verify(store).activate(1, 7);
            verify(store).activate(2, 7);
            assertEquals(java.util.Optional.of(7L), queue.baristaOf(2));
            assertFalse(queue.isWaiting(1));
        }

        @Test
        @DisplayName("a concurrent offline request made while the write was in flight is not lost")
        void offlineNotLost() {
            sessionsExist(1);
            queue.baristaReady(7);
            queue.customerWaiting(1);
            when(store.activate(1, 7)).thenAnswer(inv -> {
                queue.baristaOffline(7);
                return false;
            });

            matchmaker.heal();
            matchmaker.settle(new Match(1, 7));

            assertFalse(queue.isReady(7));
            assertFalse(queue.isBusy(7));
        }
    }

    @Nested
    @DisplayName("null arguments")
    class NullTests {

        @Test
        @DisplayName("settle(null) and settleAll(null) fail fast with a clear message")
        void nulls() {
            assertEquals("match cannot be null", assertThrows(NullPointerException.class, () -> matchmaker.settle(null)).getMessage());
            assertEquals("matches cannot be null", assertThrows(NullPointerException.class, () -> matchmaker.settleAll(null)).getMessage());
        }

        @Test
        @DisplayName("constructors reject null collaborators")
        void constructor() {
            assertThrows(NullPointerException.class, () -> new ChatMatchmaker(null, store));
            assertThrows(NullPointerException.class, () -> new ChatMatchmaker(queue, null));
        }
    }
}
