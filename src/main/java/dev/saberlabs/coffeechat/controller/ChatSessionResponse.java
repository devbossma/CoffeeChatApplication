package dev.saberlabs.coffeechat.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.saberlabs.coffeechat.chat.SessionDetail;
import dev.saberlabs.coffeechat.chat.SessionView;
import dev.saberlabs.coffeechat.model.SessionStatus;

import java.time.Instant;

/** A chat session. The names are present only where the endpoint looks them up (the "mine" read). */
public record ChatSessionResponse(Long id,
                                  SessionStatus status,
                                  Long customerId,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) String customerName,
                                  Long baristaId,
                                  @JsonInclude(JsonInclude.Include.NON_NULL) String baristaName,
                                  Instant createdAt) {

    public static ChatSessionResponse from(SessionView s) {
        return new ChatSessionResponse(s.id(), s.status(), s.customerId(), null, s.baristaId(), null, s.createdAt());
    }

    public static ChatSessionResponse from(SessionDetail d) {
        SessionView s = d.session();
        return new ChatSessionResponse(s.id(), s.status(), s.customerId(), d.customerName(), s.baristaId(), d.baristaName(), s.createdAt());
    }
}
