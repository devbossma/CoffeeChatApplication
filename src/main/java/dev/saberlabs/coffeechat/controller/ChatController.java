package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.chat.ChatService;
import dev.saberlabs.coffeechat.facade.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for chat (PRD &sect;9.3/9.7). Thin: every rule (who may do what, message validation, the
 * {@code /order} path) lives in {@link ChatService}; this maps HTTP to it and back to response records.
 * The caller is the {@code X-User-Id} actor.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** A customer asks to chat: 201 with a WAITING session, or an ACTIVE one if a barista was ready. */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse start(Actor actor) {
        return ChatSessionResponse.from(chat.startChat(actor));
    }

    /** The caller's own open session (a customer's with the barista's name, a barista's with the customer's). */
    @GetMapping("/sessions/mine")
    public ChatSessionResponse mine(Actor actor) {
        return ChatSessionResponse.from(chat.mySession(actor));
    }

    @PostMapping("/barista/ready")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void baristaReady(Actor actor) {
        chat.baristaReady(actor);
    }

    @PostMapping("/barista/offline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void baristaOffline(Actor actor) {
        chat.baristaOffline(actor);
    }

    @PostMapping("/sessions/{id}/end")
    public EndChatResponse end(@PathVariable Long id, Actor actor) {
        return new EndChatResponse(chat.endSession(actor, id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<SendMessageResponse> send(@PathVariable Long id, @Valid @RequestBody SendMessageHttpRequest request, Actor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SendMessageResponse.from(chat.sendMessage(actor, id, request.content())));
    }

    /** History, oldest first, one page at a time (default 50, at most 200). */
    @GetMapping("/sessions/{id}/messages")
    public ChatHistoryResponse history(@PathVariable Long id, Actor actor,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(ChatService.MAX_HISTORY_PAGE_SIZE) int size) {
        return new ChatHistoryResponse(page, size, chat.history(actor, id, page, size).stream().map(ChatMessageResponse::from).toList());
    }
}
