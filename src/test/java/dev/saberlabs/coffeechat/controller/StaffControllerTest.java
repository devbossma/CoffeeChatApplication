package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.StaffMember;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("StaffController")
class StaffControllerTest extends AbstractWebMvcTest {

    private static MockHttpServletRequestBuilder create(String body) {
        return post("/api/staff").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("201 with the new account and a Location header, created as the caller")
    void created() throws Exception {
        when(facade.createStaff(Actor.user(1L), "Bob", Role.BARISTA)).thenReturn(new StaffMember(3L, "Bob", Role.BARISTA));

        mvc.perform(create("{\"name\": \"Bob\", \"role\": \"BARISTA\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/staff/3"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.role").value("BARISTA"));
    }

    @Test
    @DisplayName("a manager can be created too")
    void manager() throws Exception {
        when(facade.createStaff(Actor.user(1L), "Mo", Role.MANAGER)).thenReturn(new StaffMember(4L, "Mo", Role.MANAGER));

        mvc.perform(create("{\"name\": \"Mo\", \"role\": \"MANAGER\"}")).andExpect(status().isCreated());
        verify(facade).createStaff(Actor.user(1L), "Mo", Role.MANAGER);
    }

    @Test
    @DisplayName("401 without identity or for an unknown user, 403 for a non-manager")
    void identity() throws Exception {
        mvc.perform(post("/api/staff").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Bob\", \"role\": \"BARISTA\"}"))
                .andExpect(status().isUnauthorized());

        doThrow(UnknownActorException.noSuchUser(1L)).when(facade).createStaff(any(), any(), any());
        mvc.perform(create("{\"name\": \"Bob\", \"role\": \"BARISTA\"}")).andExpect(status().isUnauthorized());

        doThrow(new RoleNotAllowedException(1L, Role.CUSTOMER, "create staff")).when(facade).createStaff(any(), any(), any());
        mvc.perform(create("{\"name\": \"Bob\", \"role\": \"BARISTA\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("400 for a blank or missing name, a name over 255 characters, a missing/CUSTOMER/unknown role, malformed JSON and no body")
    void invalid() throws Exception {
        for (String body : new String[]{"{\"name\": \"  \", \"role\": \"BARISTA\"}", "{\"role\": \"BARISTA\"}",
                "{\"name\": \"" + "x".repeat(256) + "\", \"role\": \"BARISTA\"}", "{\"name\": \"Bob\"}",
                "{\"name\": \"Bob\", \"role\": \"CUSTOMER\"}", "{\"name\": \"Bob\", \"role\": \"ADMIN\"}", "{oops", ""}) {
            mvc.perform(create(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(facade);
    }
}
