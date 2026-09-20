package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.chat.ChatSessionAlreadyOpenException;
import dev.saberlabs.coffeechat.chat.ChatSessionNotFoundException;
import dev.saberlabs.coffeechat.chat.NotChatParticipantException;
import dev.saberlabs.coffeechat.chat.SessionNotActiveException;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP mapping of the chat exceptions, checked with a throw-away controller and the real
 * {@link RestExceptionHandler}: no Spring context is built, so it does not add to the context count.
 */
@DisplayName("RestExceptionHandler: chat exceptions")
class ChatExceptionMappingTest {

    @RestController
    static class Thrower {
        @GetMapping("/boom/{kind}")
        String boom(@PathVariable String kind) {
            throw switch (kind) {
                case "open" -> new ChatSessionAlreadyOpenException(3L, 9L);
                case "open-unknown" -> new ChatSessionAlreadyOpenException(3L, null);
                case "inactive" -> new SessionNotActiveException(9L, SessionStatus.WAITING);
                case "participant" -> new NotChatParticipantException(3L, 9L, "post to it");
                case "missing" -> new ChatSessionNotFoundException(9L);
                case "blank" -> new IllegalArgumentException("A chat message cannot be blank");
                default -> new IllegalStateException(kind);
            };
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Thrower()).setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    @DisplayName("an already-open chat is 409 and carries the existing session id")
    void alreadyOpen() throws Exception {
        mvc.perform(get("/boom/open")).andExpect(status().isConflict()).andExpect(jsonPath("$.existingSessionId").value(9));
    }

    @Test
    @DisplayName("an already-open chat whose session vanished is still 409, with a null id")
    void alreadyOpenUnknownId() throws Exception {
        mvc.perform(get("/boom/open-unknown")).andExpect(status().isConflict()).andExpect(jsonPath("$.existingSessionId").doesNotExist());
    }

    @Test
    @DisplayName("a session that is not ACTIVE is 409")
    void notActive() throws Exception {
        mvc.perform(get("/boom/inactive")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a non-participant is 403")
    void notParticipant() throws Exception {
        mvc.perform(get("/boom/participant")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unknown session is 404")
    void notFound() throws Exception {
        mvc.perform(get("/boom/missing")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an invalid message is 400")
    void invalidMessage() throws Exception {
        mvc.perform(get("/boom/blank")).andExpect(status().isBadRequest());
    }
}
