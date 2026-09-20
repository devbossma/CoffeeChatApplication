package dev.saberlabs.coffeechat.chat;

import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a customer who already has a non-INACTIVE chat session tries to start another. Maps to
 * HTTP 409 and carries the existing session's id so the client can simply go back to it.
 */
public class ChatSessionAlreadyOpenException extends RuntimeException {

    private final Long customerId;
    private final Long existingSessionId;

    public ChatSessionAlreadyOpenException(Long customerId, @Nullable Long existingSessionId) {
        super("Customer " + customerId + " already has an open chat session"
                + (existingSessionId == null ? "" : " (" + existingSessionId + ")"));
        this.customerId = customerId;
        this.existingSessionId = existingSessionId;
    }

    public Long customerId() {
        return customerId;
    }

    /** The open session, or null if it ended again in the instant between the conflict and the lookup. */
    public @Nullable Long existingSessionId() {
        return existingSessionId;
    }
}
