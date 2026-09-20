package dev.saberlabs.coffeechat.model;

/**
 * Distinguishes a human-typed chat message from an automated confirmation/error reply within the
 * same session (PRD &sect;6, carried over from {@code MyDesignPattern}'s {@code chat.MessageType}).
 */
public enum MessageType {
    CHAT_MESSAGE,
    SYSTEM_MESSAGE
}
