package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.BaristaQueue.Match;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;

/**
 * The matching protocol: {@link BaristaQueue} decides (in memory, under its lock), {@link ChatSessionStore}
 * makes the decision durable (one conditional UPDATE, outside that lock), and this class connects the two
 * and compensates when they disagree. It is deliberately not {@code @Transactional}: the queue step and
 * the database step are separate on purpose, and a failure in the second must be able to undo the first.
 *
 * <p>Every path that changes {@code chat_sessions.status} is here (via the store): opening (WAITING),
 * matching (ACTIVE) and ending (INACTIVE).
 *
 * <p><b>Failures of the durable step, and what happens to the pair:</b>
 * <ul>
 *   <li><em>The session is no longer WAITING</em> (it ended in between): the match is dropped, the barista
 *       goes back to the front of the ready line and is paired with the next waiting customer, all in one
 *       queue operation.</li>
 *   <li><em>The barista can never serve</em> ({@link ChatBaristaUnavailableException}): permanent, so the
 *       barista is dropped from the queue and the customer keeps their place at the front.</li>
 *   <li><em>Anything else</em> (a transient database error): the pair goes back to the front of their lines,
 *       the error is logged and NOT rethrown (the caller's own request already succeeded: the session exists
 *       or the barista is registered), and the pair is retried by the next operation, which starts with
 *       {@link BaristaQueue#pairPending()}; no timer is needed and nobody can overtake them.</li>
 * </ul>
 */
@Service
public class ChatMatchmaker {

    private static final Logger log = LoggerFactory.getLogger(ChatMatchmaker.class);

    private final BaristaQueue queue;
    private final ChatSessionStore store;

    public ChatMatchmaker(@NotNull BaristaQueue queue, @NotNull ChatSessionStore store) {
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    /**
     * Opens a session for the customer and matches it at once if a barista is ready. Returns its current
     * state. Never fails because a match could not be written: the WAITING session is returned and the
     * pairing is retried by the next operation.
     */
    public SessionView open(long customerId) {
        heal();
        SessionView created = store.createWaiting(customerId);
        settleAll(queue.customerWaiting(created.id()));
        return store.find(created.id()).orElse(created);
    }

    /** A barista becomes available; matched at once with the longest-waiting customer, if any. */
    public void baristaReady(long baristaId) {
        heal();
        settleAll(queue.baristaReady(baristaId));
    }

    public void baristaOffline(long baristaId) {
        queue.baristaOffline(baristaId);
    }

    /**
     * Ends a session: database first (the conditional UPDATE decides who ended it), then the queue frees the
     * barista and rematches them. Safe to call twice; the queue step runs regardless so it also heals a
     * queue that is out of step.
     *
     * @return true if this call ended the session
     */
    public boolean end(long sessionId) {
        heal();
        boolean ended = store.end(sessionId);
        settleAll(queue.sessionEnded(sessionId));
        return ended;
    }

    /** Retries any pair a transient failure left side by side. */
    void heal() {
        settleAll(queue.pairPending());
    }

    void settleAll(@NotNull Collection<Match> matches) {
        Objects.requireNonNull(matches, "matches cannot be null");
        Deque<Match> work = new ArrayDeque<>(matches);
        drain(work);
    }

    /** Makes one tentative match durable (and whatever its failure handling pairs next). */
    void settle(@NotNull Match match) {
        Objects.requireNonNull(match, "match cannot be null");
        Deque<Match> work = new ArrayDeque<>();
        work.add(match);
        drain(work);
    }

    private void drain(Deque<Match> work) {
        while (!work.isEmpty()) {
            Match match = work.poll();
            try {
                if (!store.activate(match.sessionId(), match.baristaId())) {
                    work.addAll(queue.matchFailed(match, false));
                }
            } catch (ChatBaristaUnavailableException e) {
                log.warn("Dropping barista {} from the chat queue: {}", match.baristaId(), e.getMessage());
                work.addAll(queue.baristaRejected(match));
            } catch (RuntimeException e) {
                log.error("Could not make match {} durable; it will be retried by the next chat operation", match, e);
                queue.matchFailed(match, true);
                return;
            }
        }
    }
}
