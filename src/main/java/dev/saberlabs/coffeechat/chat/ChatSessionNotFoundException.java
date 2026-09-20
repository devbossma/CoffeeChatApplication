package dev.saberlabs.coffeechat.chat;

/** No chat session has that id. Maps to HTTP 404. */
public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(long sessionId) {
        super("No chat session with id " + sessionId);
    }

    private ChatSessionNotFoundException(String message) {
        super(message);
    }

    /** The user has no open chat session (nothing to look up by id). */
    public static ChatSessionNotFoundException noOpenSessionFor(long userId) {
        return new ChatSessionNotFoundException("User " + userId + " has no open chat session");
    }
}
