package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminShopController.class)
@DisplayName("AdminShopController")
class AdminShopControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CoffeeShop coffeeShop;

    @Nested
    @DisplayName("POST /api/admin/shop/close")
    class CloseTests {

        @Test
        @DisplayName("closes the shop and reports the new state")
        void closes() throws Exception {
            when(coffeeShop.isOpen()).thenReturn(false);
            when(coffeeShop.activeMenu()).thenReturn(Set.of(CoffeeType.ESPRESSO));

            mvc.perform(post("/api/admin/shop/close"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(false));

            verify(coffeeShop).close();
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

            mvc.perform(post("/api/admin/shop/open"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(true));

            verify(coffeeShop).open();
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
    }
}
