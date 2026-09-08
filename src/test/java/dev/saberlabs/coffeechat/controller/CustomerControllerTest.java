package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerService customers;

    @Nested
    @DisplayName("POST /api/customers")
    class CreateTests {

        @Test
        @DisplayName("201 with the new customer, defaulting to REGULAR / 0 orders")
        void created() throws Exception {
            Customer created = new Customer("Alice");
            created.assignId(1L);
            when(customers.create("Alice")).thenReturn(created);

            mvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "Alice"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Alice"))
                    .andExpect(jsonPath("$.fulfilledOrders").value(0))
                    .andExpect(jsonPath("$.loyaltyTier").value("REGULAR"));
        }

        @Test
        @DisplayName("400 when the name is blank")
        void blankName() throws Exception {
            mvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "   "}"""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 when the service rejects the name")
        void serviceRejects() throws Exception {
            when(customers.create(anyString())).thenThrow(new IllegalArgumentException("bad name"));

            mvc.perform(post("/api/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "x"}"""))
                    .andExpect(status().isBadRequest());
        }
    }
}
