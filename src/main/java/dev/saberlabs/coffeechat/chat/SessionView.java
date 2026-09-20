package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.SessionStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** A detached, lazy-proof snapshot of a {@code chat_sessions} row. */
public record SessionView(long id, long customerId, @Nullable Long baristaId, SessionStatus status, Instant createdAt) {
}
