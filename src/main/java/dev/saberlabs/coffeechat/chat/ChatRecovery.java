package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.SessionStatus;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Restart recovery for chat: {@link BaristaQueue} is in memory, the sessions are not. ACTIVE sessions get
 * their barista back as BUSY; WAITING sessions rejoin the line oldest first. Ready baristas are NOT
 * recoverable (nothing durable says who was ready) and must register again: the documented limitation.
 * Idempotent, so running it twice changes nothing.
 */
@Component
public class ChatRecovery {

    private static final Logger log = LoggerFactory.getLogger(ChatRecovery.class);

    private final ChatSessionStore store;
    private final BaristaQueue queue;
    private final ChatMatchmaker matchmaker;

    public ChatRecovery(@NotNull ChatSessionStore store, @NotNull BaristaQueue queue, @NotNull ChatMatchmaker matchmaker) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.queue = Objects.requireNonNull(queue, "queue cannot be null");
        this.matchmaker = Objects.requireNonNull(matchmaker, "matchmaker cannot be null");
    }

    /** @return the number of sessions restored (active + waiting) */
    @EventListener(ApplicationReadyEvent.class)
    public int recover() {
        List<SessionView> active = store.findByStatus(SessionStatus.ACTIVE);
        for (SessionView s : active) {
            if (s.baristaId() != null) {
                queue.restoreActiveAssignment(s.id(), s.baristaId());
            }
        }
        List<SessionView> waiting = store.findByStatus(SessionStatus.WAITING);
        for (SessionView s : waiting) {
            queue.customerWaiting(s.id()).ifPresent(matchmaker::settle);
        }
        if (!active.isEmpty() || !waiting.isEmpty()) {
            log.info("Recovered chat state: {} active, {} waiting session(s)", active.size(), waiting.size());
        }
        return active.size() + waiting.size();
    }
}
