package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ChatRecovery (faked store: failures at startup)")
class ChatRecoveryTest {

    private BaristaQueue queue;
    private ChatSessionStore store;
    private ChatRecovery recovery;

    @BeforeEach
    void setUp() {
        queue = new BaristaQueue();
        store = mock(ChatSessionStore.class);
        recovery = new ChatRecovery(store, queue, new ChatMatchmaker(queue, store));
    }

    private static SessionView session(long id, Long barista, SessionStatus status) {
        return new SessionView(id, id + 1000, barista, status, Instant.now());
    }

    @Test
    @DisplayName("a database failure while loading sessions never aborts startup, and what could be read is still restored")
    void loadFailureSwallowed() {
        when(store.findByStatus(SessionStatus.ACTIVE)).thenThrow(new IllegalStateException("db down"));
        when(store.findByStatus(SessionStatus.WAITING)).thenReturn(List.of(session(2, null, SessionStatus.WAITING)));

        assertDoesNotThrow(recovery::recover);

        assertTrue(queue.isWaiting(2));
    }

    @Test
    @DisplayName("a failure loading the waiting sessions is swallowed too")
    void waitingLoadFailureSwallowed() {
        when(store.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of(session(1, 7L, SessionStatus.ACTIVE)));
        when(store.findByStatus(SessionStatus.WAITING)).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(recovery::recover);

        assertTrue(queue.isBusy(7));
    }

    @Test
    @DisplayName("restores active sessions as BUSY and waiting ones in order; an ACTIVE row without a barista is skipped")
    void restores() {
        when(store.findByStatus(SessionStatus.ACTIVE)).thenReturn(List.of(
                session(1, 7L, SessionStatus.ACTIVE), session(9, null, SessionStatus.ACTIVE)));
        when(store.findByStatus(SessionStatus.WAITING)).thenReturn(List.of(
                session(2, null, SessionStatus.WAITING), session(3, null, SessionStatus.WAITING)));

        recovery.recover();

        assertTrue(queue.isBusy(7));
        assertEquals(2, queue.waitingCount());
        assertEquals(List.of(new BaristaQueue.Match(2, 8)), queue.baristaReady(8));
    }

    @Test
    @DisplayName("constructor rejects null collaborators")
    void constructorNulls() {
        ChatMatchmaker matchmaker = new ChatMatchmaker(queue, store);
        assertThrows(NullPointerException.class, () -> new ChatRecovery(null, queue, matchmaker));
        assertThrows(NullPointerException.class, () -> new ChatRecovery(store, null, matchmaker));
        assertThrows(NullPointerException.class, () -> new ChatRecovery(store, queue, null));
    }
}
