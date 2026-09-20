package dev.saberlabs.coffeechat.chat;

/**
 * A barista offered to a session can never serve it: no such user, not a BARISTA, or already ACTIVE with
 * another customer in the database. A PERMANENT failure (retrying with the same barista cannot succeed),
 * as opposed to a transient database error; the matchmaker drops such a barista from the queue.
 */
public class ChatBaristaUnavailableException extends RuntimeException {

    public ChatBaristaUnavailableException(long baristaId, String reason) {
        super("Barista " + baristaId + " cannot take a chat: " + reason);
    }
}
