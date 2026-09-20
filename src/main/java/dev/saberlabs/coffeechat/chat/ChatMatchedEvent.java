package dev.saberlabs.coffeechat.chat;

/** A customer session was durably paired with a barista. Published inside the matching transaction. */
public record ChatMatchedEvent(long sessionId, long customerId, long baristaId) {
}
