package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.model.MessageType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/** A detached snapshot of a {@code chat_messages} row. {@code senderId} is null for a system message. */
public record MessageView(long id, long sessionId, MessageType type, @Nullable Long senderId, String senderName,
                          String content, Instant sentAt, @Nullable Long orderId) {
}
