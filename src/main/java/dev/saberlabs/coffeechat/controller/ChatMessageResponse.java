package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.chat.MessageView;
import dev.saberlabs.coffeechat.model.MessageType;

import java.time.Instant;

/** One chat message; {@code senderId} is null for a system message, {@code orderId} for one not linked to an order. */
public record ChatMessageResponse(Long id, Long sessionId, MessageType type, Long senderId, String senderName,
                                  String content, Instant sentAt, Long orderId) {

    public static ChatMessageResponse from(MessageView m) {
        return new ChatMessageResponse(m.id(), m.sessionId(), m.type(), m.senderId(), m.senderName(), m.content(), m.sentAt(), m.orderId());
    }
}
