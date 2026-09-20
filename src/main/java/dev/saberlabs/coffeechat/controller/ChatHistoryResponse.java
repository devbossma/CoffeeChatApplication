package dev.saberlabs.coffeechat.controller;

import java.util.List;

/** One page of a session's history, oldest first. A page shorter than {@code size} is the last. */
public record ChatHistoryResponse(int page, int size, List<ChatMessageResponse> messages) {
}
