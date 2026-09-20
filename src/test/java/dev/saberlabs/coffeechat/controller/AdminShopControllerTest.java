package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminShopController")
class AdminShopControllerTest extends AbstractWebMvcTest {




    @Nested
    @DisplayName("POST /api/admin/shop/close")
    class CloseTests {

        @Test
        @DisplayName("closes the shop and reports the new state")
        void closes() throws Exception {
            when(coffeeShop.isOpen()).thenReturn(false);
            when(coffeeShop.activeMenu()).thenReturn(Set.of(CoffeeType.ESPRESSO));

            mvc.perform(post("/api/admin/shop/close").header("X-User-Id", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(false));

            verify(facade).closeShop(Actor.user(5L));
        }

        @Test
        @DisplayName("401 without an X-User-Id header, and the shop is untouched")
        void anonymous() throws Exception {
            mvc.perform(post("/api/admin/shop/close")).andExpect(status().isUnauthorized());

            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("401 for a non-numeric X-User-Id")
        void badHeader() throws Exception {
            mvc.perform(post("/api/admin/shop/close").header("X-User-Id", "boss")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("401 when the user does not exist")
        void unknownUser() throws Exception {
            doThrow(UnknownActorException.noSuchUser(9L)).when(facade).closeShop(any());

            mvc.perform(post("/api/admin/shop/close").header("X-User-Id", "9")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 when the caller is not a MANAGER")
        void forbidden() throws Exception {
            doThrow(new RoleNotAllowedException(3L, Role.CUSTOMER, "close the shop")).when(facade).closeShop(any());

            mvc.perform(post("/api/admin/shop/close").header("X-User-Id", "3"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/shop/open")
    class OpenTests {

        @Test
        @DisplayName("opens the shop and reports the new state")
        void opens() throws Exception {
            when(coffeeShop.isOpen()).thenReturn(true);
            when(coffeeShop.activeMenu()).thenReturn(Set.of(CoffeeType.values()));

            mvc.perform(post("/api/admin/shop/open").header("X-User-Id", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(true));

            verify(facade).openShop(Actor.user(5L));
        }

        @Test
        @DisplayName("401 without an X-User-Id header")
        void anonymous() throws Exception {
            mvc.perform(post("/api/admin/shop/open")).andExpect(status().isUnauthorized());

            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("403 when the caller is not a MANAGER")
        void forbidden() throws Exception {
            doThrow(new RoleNotAllowedException(3L, Role.BARISTA, "open the shop")).when(facade).openShop(any());

            mvc.perform(post("/api/admin/shop/open").header("X-User-Id", "3")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/shop")
    class StateTests {

        @Test
        @DisplayName("reports the current open flag and menu without mutating anything")
        void reportsState() throws Exception {
            when(coffeeShop.isOpen()).thenReturn(true);
            when(coffeeShop.activeMenu()).thenReturn(Set.of(CoffeeType.ESPRESSO, CoffeeType.LATTE));

            mvc.perform(get("/api/admin/shop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(true))
                    .andExpect(jsonPath("$.menu").isArray());
        }

        @Test
        @DisplayName("needs no identity")
        void open() throws Exception {
            when(coffeeShop.isOpen()).thenReturn(false);
            when(coffeeShop.activeMenu()).thenReturn(Set.of());

            mvc.perform(get("/api/admin/shop")).andExpect(status().isOk());
        }
    }
}
