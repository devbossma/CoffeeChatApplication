package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.BaristaQueue.Match;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li><em>Anything else</em> (a transient database error): EVERY tentative match of the batch goes back to
 *       the front of its lines in the original order, the error is logged and NOT rethrown (the caller's own
 *       request already succeeded), and the pair is retried by the next operation, which starts with
 *       {@link BaristaQueue#pairPending()}; no timer is needed and nobody can overtake them. After
 *       {@code chat.match.max-attempts} (default 3) consecutive failures of the same pair, it is treated as
 *       permanent: the barista is dropped, an ERROR is logged, and the customer is paired with the next one.</li>
 * </ul>
 */
@Service
public class ChatMatchmaker {

    private static final Logger log = LoggerFactory.getLogger(ChatMatchmaker.class);

    static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final BaristaQueue queue;
    private final ChatSessionStore store;
    private final int maxAttempts;
    /** Consecutive transient failures per tentative pair; cleared on success or when the pair is given up. */
    private final Map<Match, Integer> failures = new ConcurrentHashMap<>();

    @Autowired
    public ChatMatchmaker(@NotNull BaristaQueue queue, @NotNull ChatSessionStore store,
                          @Value("${chat.match.max-attempts:" + DEFAULT_MAX_ATTEMPTS + "}") int maxAttempts) {
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
        this.store = Objects.requireNonNull(store, "store cannot be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("chat.match.max-attempts must be at least 1: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
    }

    public ChatMatchmaker(@NotNull BaristaQueue queue, @NotNull ChatSessionStore store) {
        this(queue, store, DEFAULT_MAX_ATTEMPTS);
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
                if (store.activate(match.sessionId(), match.baristaId())) {
                    failures.remove(match);
                } else {
                    failures.remove(match);
                    work.addAll(queue.matchFailed(match, false));
                }
            } catch (ChatBaristaUnavailableException e) {
                log.warn("Dropping barista {} from the chat queue: {}", match.baristaId(), e.getMessage());
                failures.remove(match);
                work.addAll(queue.baristaRejected(match));
            } catch (RuntimeException e) {
                transientFailure(match, work, e);
            }
        }
    }

    /**
     * A write failed for a reason that is not the barista's. Every match still in the batch is tentative in
     * the queue but was never written, so they ALL go back (last first, so the first ends up at the front and
     * FIFO holds), not just the failed one; otherwise those customers would be stuck WAITING in the database
     * while their baristas sat BUSY in memory. The failed pair is retried by the next operation, but only
     * {@code maxAttempts} times in a row: a failure that persists for one pair (a constraint nobody mapped)
     * would otherwise block the head of both lines forever, so after that the barista is dropped as if
     * permanently unusable and the customer is paired with the next ready barista.
     */
    private void transientFailure(Match failed, Deque<Match> work, RuntimeException e) {
        List<Match> rest = new ArrayList<>(work);
        work.clear();
        Collections.reverse(rest);
        for (Match m : rest) {
            queue.matchFailed(m, true);
        }
        int attempts = failures.merge(failed, 1, Integer::sum);
        if (attempts >= maxAttempts) {
            failures.remove(failed);
            log.error("Match of session {} with barista {} failed {} times in a row; dropping barista {} from the queue",
                    failed.sessionId(), failed.baristaId(), attempts, failed.baristaId(), e);
            work.addAll(queue.baristaRejected(failed));
        } else {
            log.error("Could not make match {} durable (attempt {}/{}); it will be retried by the next chat operation",
                    failed, attempts, maxAttempts, e);
            queue.matchFailed(failed, true);
        }
    }
}
