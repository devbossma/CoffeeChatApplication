package dev.saberlabs.coffeechat.chat;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The live matching state for chat: which customers are waiting and which baristas are free, FIFO on
 * both sides. Ported from the reference {@code BaristaQueue}, with three deliberate differences:
 *
 * <ul>
 *   <li><b>It only DECIDES.</b> It holds session and barista <em>ids</em>, returns a {@link Match}, and
 *       performs no I/O inside its lock. Making the decision durable (the conditional database update)
 *       is {@code ChatSessionStore}'s job, done outside the lock; if that fails,
 *       {@link #matchFailed} undoes the decision. It is coordination state that can always be rebuilt
 *       from the database, so it is correctly in memory (PRD 9.3).</li>
 *   <li><b>A barista is in exactly one state</b> (READY, BUSY, or absent). The reference {@code offer()}ed a
 *       barista into the ready deque on every registration, so one registered twice could be matched to
 *       two customers at once. Here registering while READY or BUSY is an idempotent no-op.</li>
 *   <li><b>Offline while BUSY is remembered</b>: the reference put a barista who had asked to go offline
 *       back into READY when their session ended; here they stay out.</li>
 * </ul>
 *
 * <p>One fair lock guards every structure, since a match touches both sides atomically. A returned
 * {@link Match} is <em>tentative</em>: both parties are already recorded as BUSY/ACTIVE so nothing else
 * can claim them, until the caller either keeps it or calls {@link #matchFailed}.
 */
@Component
public class BaristaQueue {

    /** A customer session paired with a barista. Tentative until the database write commits. */
    public record Match(long sessionId, long baristaId) {
    }

    private final ReentrantLock lock = new ReentrantLock(true);

    private final Deque<Long> readyBaristas = new ArrayDeque<>();
    private final Set<Long> readyIds = new HashSet<>();
    private final Deque<Long> waitingSessions = new ArrayDeque<>();
    private final Set<Long> waitingIds = new HashSet<>();
    private final Map<Long, Long> baristaBySession = new HashMap<>();
    private final Map<Long, Long> sessionByBarista = new HashMap<>();
    /** Baristas who asked to go offline while BUSY: not returned to READY when their session ends. */
    private final Set<Long> offlineWhenFree = new HashSet<>();

    /**
     * A barista becomes available. Matched at once with the longest-waiting customer, if any.
     * Idempotent: a barista who is already READY or BUSY is left as they are.
     */
    public Optional<Match> baristaReady(long baristaId) {
        lock.lock();
        try {
            if (readyIds.contains(baristaId) || sessionByBarista.containsKey(baristaId)) {
                return Optional.empty();
            }
            Long sessionId = waitingSessions.poll();
            if (sessionId != null) {
                waitingIds.remove(sessionId);
                return Optional.of(assign(sessionId, baristaId));
            }
            readyBaristas.offer(baristaId);
            readyIds.add(baristaId);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A customer session starts waiting. Matched at once with the longest-ready barista, if any.
     * Idempotent: a session that is already waiting or already matched is left as it is.
     */
    public Optional<Match> customerWaiting(long sessionId) {
        lock.lock();
        try {
            if (waitingIds.contains(sessionId) || baristaBySession.containsKey(sessionId)) {
                return Optional.empty();
            }
            Long baristaId = readyBaristas.poll();
            if (baristaId != null) {
                readyIds.remove(baristaId);
                return Optional.of(assign(sessionId, baristaId));
            }
            waitingSessions.offer(sessionId);
            waitingIds.add(sessionId);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A session ended. If it was still WAITING it is removed from the waiting line, so no barista can
     * ever be matched to a dead session. If it was matched, the barista is freed and immediately
     * rematched with the next waiting customer, unless they asked to go offline meanwhile. Unknown
     * sessions and a second call for the same session are no-ops.
     */
    public Optional<Match> sessionEnded(long sessionId) {
        lock.lock();
        try {
            if (waitingIds.remove(sessionId)) {
                waitingSessions.remove(sessionId);
                return Optional.empty();
            }
            Long baristaId = baristaBySession.remove(sessionId);
            if (baristaId == null) {
                return Optional.empty();
            }
            sessionByBarista.remove(baristaId);
            if (offlineWhenFree.remove(baristaId)) {
                return Optional.empty();
            }
            return baristaReady(baristaId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * A barista goes offline. A READY barista leaves the pool; a BUSY one keeps their session but will
     * not be returned to READY when it ends. A barista who is not in the queue is a no-op.
     */
    public void baristaOffline(long baristaId) {
        lock.lock();
        try {
            if (readyIds.remove(baristaId)) {
                readyBaristas.remove(baristaId);
            } else if (sessionByBarista.containsKey(baristaId)) {
                offlineWhenFree.add(baristaId);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Undoes a {@link Match} whose database write did not happen (it failed, or the session had ended
     * in the meantime). The barista goes back to the FRONT of the ready line, keeping FIFO fairness
     * (unless they asked to go offline meanwhile). The session goes back to the FRONT of the waiting
     * line if {@code sessionStillWaiting} (the failure was transient); otherwise it is dropped.
     * A no-op if the match is no longer the current assignment (for example the session already ended).
     */
    public void matchFailed(Match match, boolean sessionStillWaiting) {
        lock.lock();
        try {
            Long current = baristaBySession.get(match.sessionId());
            if (current == null || current != match.baristaId()) {
                return;
            }
            baristaBySession.remove(match.sessionId());
            sessionByBarista.remove(match.baristaId());
            if (!offlineWhenFree.remove(match.baristaId())) {
                readyBaristas.addFirst(match.baristaId());
                readyIds.add(match.baristaId());
            }
            if (sessionStillWaiting && !waitingIds.contains(match.sessionId())) {
                waitingSessions.addFirst(match.sessionId());
                waitingIds.add(match.sessionId());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Restart recovery: a session that was ACTIVE keeps its barista, who stays BUSY (and cannot be
     * matched again) until it ends. Idempotent.
     */
    public void restoreActiveAssignment(long sessionId, long baristaId) {
        lock.lock();
        try {
            waitingIds.remove(sessionId);
            waitingSessions.remove(sessionId);
            if (readyIds.remove(baristaId)) {
                readyBaristas.remove(baristaId);
            }
            baristaBySession.put(sessionId, baristaId);
            sessionByBarista.put(baristaId, sessionId);
        } finally {
            lock.unlock();
        }
    }

    public int waitingCount() {
        lock.lock();
        try {
            return waitingSessions.size();
        } finally {
            lock.unlock();
        }
    }

    public int readyCount() {
        lock.lock();
        try {
            return readyBaristas.size();
        } finally {
            lock.unlock();
        }
    }

    public int busyCount() {
        lock.lock();
        try {
            return sessionByBarista.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isReady(long baristaId) {
        lock.lock();
        try {
            return readyIds.contains(baristaId);
        } finally {
            lock.unlock();
        }
    }

    public boolean isBusy(long baristaId) {
        lock.lock();
        try {
            return sessionByBarista.containsKey(baristaId);
        } finally {
            lock.unlock();
        }
    }

    public boolean isWaiting(long sessionId) {
        lock.lock();
        try {
            return waitingIds.contains(sessionId);
        } finally {
            lock.unlock();
        }
    }

    /** The barista currently matched to {@code sessionId}, if any. */
    public Optional<Long> baristaOf(long sessionId) {
        lock.lock();
        try {
            return Optional.ofNullable(baristaBySession.get(sessionId));
        } finally {
            lock.unlock();
        }
    }

    /** Clears everything. For tests and for a full recovery rebuild. */
    public void clear() {
        lock.lock();
        try {
            readyBaristas.clear();
            readyIds.clear();
            waitingSessions.clear();
            waitingIds.clear();
            baristaBySession.clear();
            sessionByBarista.clear();
            offlineWhenFree.clear();
        } finally {
            lock.unlock();
        }
    }

    private Match assign(long sessionId, long baristaId) {
        baristaBySession.put(sessionId, baristaId);
        sessionByBarista.put(baristaId, sessionId);
        return new Match(sessionId, baristaId);
    }
}
