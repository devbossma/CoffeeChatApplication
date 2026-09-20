package dev.saberlabs.coffeechat.controller;

/** {@code ended} is true if this call ended the session, false if it was already over (idempotent). */
public record EndChatResponse(boolean ended) {
}
