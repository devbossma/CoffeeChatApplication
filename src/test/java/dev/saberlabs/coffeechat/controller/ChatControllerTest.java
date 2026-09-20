package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.chat.ChatService;
import dev.saberlabs.coffeechat.chat.ChatService.SendResult;
import dev.saberlabs.coffeechat.chat.ChatSessionAlreadyOpenException;
import dev.saberlabs.coffeechat.chat.ChatSessionNotFoundException;
import dev.saberlabs.coffeechat.chat.InvalidChatMessageException;
import dev.saberlabs.coffeechat.chat.MessageView;
import dev.saberlabs.coffeechat.chat.NotChatParticipantException;
import dev.saberlabs.coffeechat.chat.SessionDetail;
import dev.saberlabs.coffeechat.chat.SessionNotActiveException;
import dev.saberlabs.coffeechat.chat.SessionView;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ChatController")
class ChatControllerTest extends AbstractWebMvcTest {

    private static final Actor CALLER = Actor.user(7L);



    private static MockHttpServletRequestBuilder as7(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "7");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return as7(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static SessionView session(long id, SessionStatus status, Long barista) {
        return new SessionView(id, 7L, barista, status, Instant.parse("2026-01-01T10:00:00Z"));
    }

    private static MessageView message(long id, MessageType type, Long sender, String text, Long order) {
        return new MessageView(id, 5L, type, sender, sender == null ? "System" : "Alice", text, Instant.parse("2026-01-01T10:00:00Z"), order);
    }

    @Nested
    @DisplayName("POST /api/chat/sessions")
    class StartTests {

        @Test
        @DisplayName("201 with the session; a WAITING session has no barista and no names")
        void waiting() throws Exception {
            when(chat.startChat(CALLER)).thenReturn(session(5, SessionStatus.WAITING, null));

            mvc.perform(as7(post("/api/chat/sessions")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.baristaId").doesNotExist())
                    .andExpect(jsonPath("$.customerName").doesNotExist());
        }

        @Test
        @DisplayName("201 with an ACTIVE session when a barista was ready")
        void active() throws Exception {
            when(chat.startChat(CALLER)).thenReturn(session(5, SessionStatus.ACTIVE, 9L));

            mvc.perform(as7(post("/api/chat/sessions")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.baristaId").value(9));
        }

        @Test
        @DisplayName("409 as a ProblemDetail carrying existingSessionId when they already have an open chat")
        void alreadyOpen() throws Exception {
            when(chat.startChat(CALLER)).thenThrow(new ChatSessionAlreadyOpenException(7L, 5L));

            mvc.perform(as7(post("/api/chat/sessions")))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.existingSessionId").value(5))
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @DisplayName("401 without identity, 403 for a non-customer")
        void identity() throws Exception {
            when(chat.startChat(CALLER)).thenThrow(new RoleNotAllowedException(7L, Role.BARISTA, "start a chat"));

            mvc.perform(post("/api/chat/sessions")).andExpect(status().isUnauthorized());
            mvc.perform(as7(post("/api/chat/sessions"))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/chat/sessions/mine")
    class MineTests {

        @Test
        @DisplayName("200 with both participants' names")
        void found() throws Exception {
            when(chat.mySession(CALLER)).thenReturn(new SessionDetail(session(5, SessionStatus.ACTIVE, 9L), "Alice", "Bob"));

            mvc.perform(as7(get("/api/chat/sessions/mine")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerName").value("Alice"))
                    .andExpect(jsonPath("$.baristaName").value("Bob"))
                    .andExpect(jsonPath("$.baristaId").value(9));
        }

        @Test
        @DisplayName("404 when they have no open session, 401 without identity, 403 for a manager")
        void errors() throws Exception {
            when(chat.mySession(CALLER)).thenThrow(ChatSessionNotFoundException.noOpenSessionFor(7L));

            mvc.perform(as7(get("/api/chat/sessions/mine"))).andExpect(status().isNotFound());
            mvc.perform(get("/api/chat/sessions/mine")).andExpect(status().isUnauthorized());

            doThrow(new RoleNotAllowedException(7L, Role.MANAGER, "have a chat of their own")).when(chat).mySession(CALLER);
            mvc.perform(as7(get("/api/chat/sessions/mine"))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/chat/barista/ready and /offline")
    class BaristaTests {

        @Test
        @DisplayName("204 for each, acting as the caller")
        void ok() throws Exception {
            mvc.perform(as7(post("/api/chat/barista/ready"))).andExpect(status().isNoContent());
            mvc.perform(as7(post("/api/chat/barista/offline"))).andExpect(status().isNoContent());

            verify(chat).baristaReady(CALLER);
            verify(chat).baristaOffline(CALLER);
        }

        @Test
        @DisplayName("401 without identity and 403 for a non-barista")
        void errors() throws Exception {
            doThrow(new RoleNotAllowedException(7L, Role.CUSTOMER, "x")).when(chat).baristaReady(any());

            mvc.perform(post("/api/chat/barista/ready")).andExpect(status().isUnauthorized());
            mvc.perform(as7(post("/api/chat/barista/ready"))).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/chat/sessions/{id}/end")
    class EndTests {

        @Test
        @DisplayName("200 reporting whether this call ended it")
        void ended() throws Exception {
            when(chat.endSession(CALLER, 5L)).thenReturn(true, false);

            mvc.perform(as7(post("/api/chat/sessions/5/end"))).andExpect(status().isOk()).andExpect(jsonPath("$.ended").value(true));
            mvc.perform(as7(post("/api/chat/sessions/5/end"))).andExpect(status().isOk()).andExpect(jsonPath("$.ended").value(false));
        }

        @Test
        @DisplayName("401, 403 and 404, and 400 for a non-numeric id")
        void errors() throws Exception {
            when(chat.endSession(CALLER, 6L)).thenThrow(new NotChatParticipantException(7L, 6L, "end it"));
            when(chat.endSession(CALLER, 7L)).thenThrow(new ChatSessionNotFoundException(7L));

            mvc.perform(post("/api/chat/sessions/5/end")).andExpect(status().isUnauthorized());
            mvc.perform(as7(post("/api/chat/sessions/6/end"))).andExpect(status().isForbidden());
            mvc.perform(as7(post("/api/chat/sessions/7/end"))).andExpect(status().isNotFound());
            mvc.perform(as7(post("/api/chat/sessions/abc/end"))).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/chat/sessions/{id}/messages")
    class SendTests {

        @Test
        @DisplayName("201 with the stored message")
        void plain() throws Exception {
            when(chat.sendMessage(CALLER, 5L, "hello"))
                    .thenReturn(new SendResult(message(1, MessageType.CHAT_MESSAGE, 7L, "hello", null), null, null));

            mvc.perform(json(post("/api/chat/sessions/5/messages"), "{\"content\": \"hello\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message.content").value("hello"))
                    .andExpect(jsonPath("$.reply").doesNotExist())
                    .andExpect(jsonPath("$.orderId").doesNotExist());
        }

        @Test
        @DisplayName("201 with the reply and the order id for an /order command")
        void order() throws Exception {
            when(chat.sendMessage(CALLER, 5L, "/order latte")).thenReturn(new SendResult(
                    message(1, MessageType.CHAT_MESSAGE, 7L, "/order latte", null),
                    message(2, MessageType.SYSTEM_MESSAGE, null, "Order #33 placed", 33L), 33L));

            mvc.perform(json(post("/api/chat/sessions/5/messages"), "{\"content\": \"/order latte\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(33))
                    .andExpect(jsonPath("$.reply.orderId").value(33))
                    .andExpect(jsonPath("$.reply.senderId").doesNotExist());
        }

        @Test
        @DisplayName("400 for a missing or null content, over 2000 characters, malformed JSON and no body; the service is not called")
        void invalidBodies() throws Exception {
            for (String body : new String[]{"{}", "{\"content\": null}", "{\"content\": \"" + "x".repeat(2001) + "\"}", "{oops", ""}) {
                mvc.perform(json(post("/api/chat/sessions/5/messages"), body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
            verify(chat, never()).sendMessage(any(), anyLong(), any());
        }

        @Test
        @DisplayName("400 when the service rejects the content as blank")
        void blank() throws Exception {
            when(chat.sendMessage(eq(CALLER), eq(5L), any())).thenThrow(new InvalidChatMessageException("A chat message cannot be blank"));

            mvc.perform(json(post("/api/chat/sessions/5/messages"), "{\"content\": \"\\u00a0\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("A chat message cannot be blank"));
        }

        @Test
        @DisplayName("401, 403, 404 and 409")
        void errors() throws Exception {
            when(chat.sendMessage(eq(CALLER), eq(6L), any())).thenThrow(new NotChatParticipantException(7L, 6L, "post to it"));
            when(chat.sendMessage(eq(CALLER), eq(7L), any())).thenThrow(new ChatSessionNotFoundException(7L));
            when(chat.sendMessage(eq(CALLER), eq(8L), any())).thenThrow(new SessionNotActiveException(8L, SessionStatus.WAITING));
            when(chat.sendMessage(eq(CALLER), eq(9L), any())).thenThrow(UnknownActorException.noSuchUser(7L));

            String body = "{\"content\": \"hi\"}";
            mvc.perform(post("/api/chat/sessions/5/messages").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
            mvc.perform(json(post("/api/chat/sessions/6/messages"), body)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/chat/sessions/7/messages"), body)).andExpect(status().isNotFound());
            mvc.perform(json(post("/api/chat/sessions/8/messages"), body)).andExpect(status().isConflict());
            mvc.perform(json(post("/api/chat/sessions/9/messages"), body)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/chat/sessions/{id}/messages")
    class HistoryTests {

        @Test
        @DisplayName("200 with the requested page, defaulting to page 0 of 50")
        void defaults() throws Exception {
            when(chat.history(CALLER, 5L, 0, 50)).thenReturn(List.of(message(1, MessageType.SYSTEM_MESSAGE, null, "Bob joined the chat", null)));

            mvc.perform(as7(get("/api/chat/sessions/5/messages")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(50))
                    .andExpect(jsonPath("$.messages[0].content").value("Bob joined the chat"));
        }

        @Test
        @DisplayName("passes page and size through, and accepts the largest allowed size")
        void explicit() throws Exception {
            when(chat.history(CALLER, 5L, 2, 200)).thenReturn(List.of());

            mvc.perform(as7(get("/api/chat/sessions/5/messages?page=2&size=200")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messages").isEmpty());
        }

        @Test
        @DisplayName("400 for size 0, size over the maximum, a negative size, a negative page and non-numeric values; the service is not called")
        void boundsRejected() throws Exception {
            for (String query : new String[]{"size=0", "size=201", "size=-1", "page=-1", "page=abc", "size=ten"}) {
                mvc.perform(as7(get("/api/chat/sessions/5/messages?" + query)))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
            verify(chat, never()).history(any(), anyLong(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("401, 403 and 404")
        void errors() throws Exception {
            when(chat.history(eq(CALLER), eq(6L), anyInt(), anyInt())).thenThrow(new NotChatParticipantException(7L, 6L, "read it"));
            when(chat.history(eq(CALLER), eq(7L), anyInt(), anyInt())).thenThrow(new ChatSessionNotFoundException(7L));

            mvc.perform(get("/api/chat/sessions/5/messages")).andExpect(status().isUnauthorized());
            mvc.perform(as7(get("/api/chat/sessions/6/messages"))).andExpect(status().isForbidden());
            mvc.perform(as7(get("/api/chat/sessions/7/messages"))).andExpect(status().isNotFound());
        }
    }
}
