package dev.saberlabs.coffeechat.controller;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/chat/sessions/{id}/messages}. Blankness (including Unicode-space-only content) is
 * decided by the chat service, which answers 400 with its own message.
 */
public record SendMessageHttpRequest(@NotNull @Size(max = 2000) String content) {
}
