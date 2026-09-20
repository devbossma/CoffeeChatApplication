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
     * Void on purpose: Spring publishes a listener's non-void return value as a new event. Nothing here may
     * abort application start: a database failure while reading, or one bad row, is logged and skipped.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int restored = 0;
        try {
            for (SessionView s : store.findByStatus(SessionStatus.ACTIVE)) {
                try {
                    if (s.baristaId() != null) {
                        queue.restoreActiveAssignment(s.id(), s.baristaId());
                        restored++;
                    }
                } catch (RuntimeException e) {
                    log.error("Could not restore active chat session {}", s.id(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not load active chat sessions; chat recovery of active sessions skipped", e);
        }
        try {
            for (SessionView s : store.findByStatus(SessionStatus.WAITING)) {
                try {
                    matchmaker.settleAll(queue.customerWaiting(s.id()));
                    restored++;
                } catch (RuntimeException e) {
                    log.error("Could not restore waiting chat session {}", s.id(), e);
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not load waiting chat sessions; chat recovery of waiting sessions skipped", e);
        }
        if (restored > 0) {
            log.info("Recovered chat state: {} session(s)", restored);
        }
    }
}
