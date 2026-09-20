package dev.saberlabs.coffeechat.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.saberlabs.coffeechat.chat.ChatService.SendResult;

/**
 * The stored message, and for an {@code /order} command the system reply and the placed order's id. The
 * order id is present even if the confirmation message could not be stored (then {@code reply} is absent).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageResponse(ChatMessageResponse message, ChatMessageResponse reply, Long orderId) {

    public static SendMessageResponse from(SendResult result) {
        return new SendMessageResponse(ChatMessageResponse.from(result.message()),
                result.reply() == null ? null : ChatMessageResponse.from(result.reply()), result.orderId());
    }
}
