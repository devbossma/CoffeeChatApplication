package dev.saberlabs.coffeechat.chat;

/** The user is neither the session's customer nor its assigned barista. Maps to HTTP 403. */
public class NotChatParticipantException extends RuntimeException {

    public NotChatParticipantException(long userId, long sessionId, String action) {
        super("User " + userId + " is not a participant of chat session " + sessionId + " and may not " + action);
    }
}
