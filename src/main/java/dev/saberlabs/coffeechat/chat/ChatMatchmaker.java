package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.BaristaQueue.Match;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * The matching protocol: {@link BaristaQueue} decides (in memory, under its lock), {@link ChatSessionStore}
 * makes the decision durable (one conditional UPDATE, outside that lock), and this class connects the two
 * and compensates when they disagree. It is deliberately not {@code @Transactional}: the queue step and
 * the database step are separate on purpose, and a failure in the second must be able to undo the first.
 *
 * <p>Every path that changes {@code chat_sessions.status} is here (via the store): opening (WAITING),
 * matching (ACTIVE) and ending (INACTIVE).
 *
 * <p>Known residual: if the database write of a match fails with an unexpected error, the pair is put back
 * at the front of their lines and the error is rethrown; they are matched the next time either side
 * registers or a customer arrives, not by a retry timer.
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

    /** Opens a session for the customer and matches it at once if a barista is ready. Returns its current state. */
    public SessionView open(long customerId) {
        SessionView created = store.createWaiting(customerId);
        try {
            queue.customerWaiting(created.id()).ifPresent(this::settle);
        } catch (RuntimeException e) {
            log.error("Session {} was created but could not be matched right now", created.id(), e);
            throw e;
        }
        return store.find(created.id()).orElse(created);
    }

    /** A barista becomes available; matched at once with the longest-waiting customer, if any. */
    public void baristaReady(long baristaId) {
        queue.baristaReady(baristaId).ifPresent(this::settle);
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
        boolean ended = store.end(sessionId);
        queue.sessionEnded(sessionId).ifPresent(this::settle);
        return ended;
    }

    /**
     * Makes a tentative match durable. If the session is no longer WAITING (it ended in the instant between
     * the queue's decision and the write) the barista goes back to the front of the ready line and is
     * offered to the next waiting customer. If the write fails, the match is undone and the error rethrown.
     */
    void settle(@NotNull Match first) {
        Match match = first;
        while (match != null) {
            boolean activated;
            try {
                activated = store.activate(match.sessionId(), match.baristaId());
            } catch (RuntimeException e) {
                queue.matchFailed(match, true);
                throw e;
            }
            if (activated) {
                return;
            }
            queue.matchFailed(match, false);
            match = rematch(match.baristaId());
        }
    }

    /** After a dead match: offer the (READY again) barista to the next waiting customer, unless they went offline. */
    private Match rematch(long baristaId) {
        if (!queue.isReady(baristaId)) {
            return null;
        }
        queue.baristaOffline(baristaId);
        Optional<Match> next = queue.baristaReady(baristaId);
        return next.orElse(null);
    }
}
