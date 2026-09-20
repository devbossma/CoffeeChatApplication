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

    /**
     * Void on purpose: Spring publishes a listener's non-void return value as a new event. One bad row is
     * logged and skipped so it can never abort startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<SessionView> active = store.findByStatus(SessionStatus.ACTIVE);
        int restored = 0;
        for (SessionView s : active) {
            try {
                if (s.baristaId() != null) {
                    queue.restoreActiveAssignment(s.id(), s.baristaId());
                    restored++;
                }
            } catch (RuntimeException e) {
                log.error("Could not restore active chat session {}", s.id(), e);
            }
        }
        List<SessionView> waiting = store.findByStatus(SessionStatus.WAITING);
        for (SessionView s : waiting) {
            try {
                matchmaker.settleAll(queue.customerWaiting(s.id()));
                restored++;
            } catch (RuntimeException e) {
                log.error("Could not restore waiting chat session {}", s.id(), e);
            }
        }
        if (restored > 0) {
            log.info("Recovered chat state: {} session(s) ({} active, {} waiting on record)", restored, active.size(), waiting.size());
        }
    }
}
