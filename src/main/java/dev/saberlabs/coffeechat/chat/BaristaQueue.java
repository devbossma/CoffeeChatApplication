package dev.saberlabs.coffeechat.chat;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The live matching state for chat: which customers are waiting and which baristas are free, FIFO on
 * both sides. Ported from the reference {@code BaristaQueue}, with deliberate differences:
 *
 * <ul>
 *   <li><b>It only DECIDES.</b> It holds session and barista <em>ids</em>, returns {@link Match}es, and
 *       performs no I/O inside its lock. Making a decision durable (the conditional database update) is
 *       {@code ChatSessionStore}'s job, done outside the lock; if that fails, {@link #matchFailed} or
 *       {@link #baristaRejected} undoes it. It is coordination state that can always be rebuilt from the
 *       database, so it is correctly in memory (PRD 9.3).</li>
 *   <li><b>Self-healing pairing.</b> Every operation ends by pairing the FRONT waiting session with the
 *       FRONT ready barista, repeatedly, so the oldest customer always meets the longest-ready barista,
 *       whatever order things arrived or failed in. The one exception is {@code matchFailed(match, true)},
 *       which puts a transiently failed pair back side by side WITHOUT re-pairing them at once (that would
 *       spin on a persistent failure); the very next operation, or {@link #pairPending()}, pairs them again,
 *       and they are still first in line, so a newcomer never jumps ahead of them.</li>
 *   <li><b>A barista is in exactly one state</b> (READY, BUSY, or absent). Registering while READY or BUSY
 *       never queues them twice.</li>
 *   <li><b>Offline while BUSY is remembered</b>, and forgotten again if the barista registers as ready
 *       while still busy (they changed their mind).</li>
 * </ul>
 *
 * <p>One fair lock guards every structure, since a match touches both sides atomically. A returned
 * {@link Match} is <em>tentative</em>: both parties are already recorded as BUSY/ACTIVE so nothing else
 * can claim them, until the caller either keeps it or reports it failed.
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
     * A barista becomes available. Idempotent: one who is already READY is left as they are; one who is
     * BUSY stays BUSY but any earlier "go offline when free" request is cancelled.
     *
     * @return the pairings this made, oldest customer first (usually none or one)
     */
    public List<Match> baristaReady(long baristaId) {
        lock.lock();
        try {
            if (sessionByBarista.containsKey(baristaId)) {
                offlineWhenFree.remove(baristaId);
                return List.of();
            }
            if (readyIds.add(baristaId)) {
                readyBaristas.offer(baristaId);
            }
            return pair();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A customer session starts waiting. Idempotent: a session that is already waiting or matched is left
     * as it is.
     *
     * @return the pairings this made (usually none or one)
     */
    public List<Match> customerWaiting(long sessionId) {
        lock.lock();
        try {
            if (!baristaBySession.containsKey(sessionId) && waitingIds.add(sessionId)) {
                waitingSessions.offer(sessionId);
            }
            return pair();
        } finally {
            lock.unlock();
        }
    }

    /**
     * A session ended. If it was still WAITING it is removed from the waiting line, so no barista can
     * ever be matched to a dead session. If it was matched, the barista is freed and rematched with the
     * next waiting customer, unless they asked to go offline meanwhile. Unknown sessions and a second call
     * for the same session are no-ops (apart from pairing anything pending).
     */
    public List<Match> sessionEnded(long sessionId) {
        lock.lock();
        try {
            if (waitingIds.remove(sessionId)) {
                waitingSessions.remove(sessionId);
            } else {
                Long baristaId = baristaBySession.remove(sessionId);
                if (baristaId != null) {
                    sessionByBarista.remove(baristaId);
                    if (!offlineWhenFree.remove(baristaId) && readyIds.add(baristaId)) {
                        readyBaristas.offer(baristaId);
                    }
                }
            }
            return pair();
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
     * Pairs whatever is pending: the front waiting session with the front ready barista, repeatedly. Called
     * before new work is accepted, so a pair left side by side by a transient failure is retried without a
     * timer.
     */
    public List<Match> pairPending() {
        lock.lock();
        try {
            return pair();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Undoes a {@link Match} whose database write did not happen. The barista goes back to the FRONT of the
     * ready line (unless they asked to go offline meanwhile). If {@code sessionStillWaiting} the failure was
     * transient: the session goes back to the FRONT of the waiting line and the pair is left side by side,
     * for the next operation to pair again, oldest first. Otherwise the session is dropped (it ended) and the
     * barista is paired at once with the next waiting customer.
     *
     * <p>A no-op, returning nothing, if the match is no longer the current assignment.
     *
     * @return the pairings this made (only when the session was dropped)
     */
    public List<Match> matchFailed(Match match, boolean sessionStillWaiting) {
        Objects.requireNonNull(match, "match cannot be null");
        lock.lock();
        try {
            if (!release(match)) {
                return List.of();
            }
            if (!offlineWhenFree.remove(match.baristaId()) && readyIds.add(match.baristaId())) {
                readyBaristas.addFirst(match.baristaId());
            }
            if (sessionStillWaiting) {
                if (waitingIds.add(match.sessionId())) {
                    waitingSessions.addFirst(match.sessionId());
                }
                return List.of();
            }
            return pair();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Undoes a {@link Match} because the BARISTA can never serve it (no such user, not a barista): the
     * barista is dropped from the queue entirely, so they are not offered again, and the session goes back
     * to the FRONT of the waiting line and is paired at once with the next ready barista.
     *
     * @return the pairings this made
     */
    public List<Match> baristaRejected(Match match) {
        Objects.requireNonNull(match, "match cannot be null");
        lock.lock();
        try {
            if (!release(match)) {
                return List.of();
            }
            offlineWhenFree.remove(match.baristaId());
            if (waitingIds.add(match.sessionId())) {
                waitingSessions.addFirst(match.sessionId());
            }
            return pair();
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

    /** Caller holds the lock. Frees the assignment if {@code match} is still current. */
    private boolean release(Match match) {
        Long current = baristaBySession.get(match.sessionId());
        if (current == null || current != match.baristaId()) {
            return false;
        }
        baristaBySession.remove(match.sessionId());
        sessionByBarista.remove(match.baristaId());
        return true;
    }

    /** Caller holds the lock. Front waiting with front ready, until one side is empty. */
    private List<Match> pair() {
        List<Match> made = new ArrayList<>();
        while (!waitingSessions.isEmpty() && !readyBaristas.isEmpty()) {
            long sessionId = waitingSessions.poll();
            long baristaId = readyBaristas.poll();
            waitingIds.remove(sessionId);
            readyIds.remove(baristaId);
            baristaBySession.put(sessionId, baristaId);
            sessionByBarista.put(baristaId, sessionId);
            made.add(new Match(sessionId, baristaId));
        }
        return made;
    }
}
