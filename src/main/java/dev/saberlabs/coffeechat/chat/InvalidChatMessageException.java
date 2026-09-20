package dev.saberlabs.coffeechat.chat;

/**
 * A chat message that cannot be accepted: null, blank (including only Unicode spaces) or too long. Maps to
 * HTTP 400. A dedicated type, so the HTTP layer does not have to treat every {@code IllegalArgumentException}
 * (which may be a programming error) as the client's fault.
 */
public class InvalidChatMessageException extends RuntimeException {

    public InvalidChatMessageException(String message) {
        super(message);
    }
}
