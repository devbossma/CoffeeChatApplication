package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.SessionStatus;

/** Messages can only be posted to an ACTIVE session (matched, not ended). Maps to HTTP 409. */
public class SessionNotActiveException extends RuntimeException {

    public SessionNotActiveException(long sessionId, SessionStatus status) {
        super("Chat session " + sessionId + " is " + status + ", not ACTIVE");
    }
}
