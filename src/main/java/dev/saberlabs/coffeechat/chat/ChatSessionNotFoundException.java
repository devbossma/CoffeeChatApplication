package dev.saberlabs.coffeechat.chat;

/** No chat session has that id. Maps to HTTP 404. */
public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(long sessionId) {
        super("No chat session with id " + sessionId);
    }
}
