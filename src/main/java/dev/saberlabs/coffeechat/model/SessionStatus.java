package dev.saberlabs.coffeechat.model;

/**
 * A chat session's lifecycle (PRD &sect;6, carried over from {@code MyDesignPattern}'s
 * {@code chat.SessionStatus}):
 *
 * <ul>
 *   <li>{@code WAITING} &mdash; the customer is queued, no barista assigned yet.</li>
 *   <li>{@code ACTIVE} &mdash; a barista is assigned and the conversation is ongoing.</li>
 *   <li>{@code INACTIVE} &mdash; the session has ended.</li>
 * </ul>
 *
 * <p>{@code BaristaQueue} (in-memory FIFO matching) is the only writer of this status on
 * {@code ChatSessionEntity} ({@code CLAUDE.md} &rarr; one source of truth per fact).
 */
public enum SessionStatus {
    WAITING,
    ACTIVE,
    INACTIVE
}
