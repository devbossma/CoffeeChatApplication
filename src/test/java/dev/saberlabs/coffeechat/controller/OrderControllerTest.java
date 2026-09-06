package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.CoffeeNotOnMenuException;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.facade.ShopClosedException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.support.TestOrders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CoffeeShopFacade facade;

    private static Order sampleOrder() {
        return TestOrders.placedEspresso(1L, TestOrders.customer(7L));
    }

    @Nested
    @DisplayName("POST /api/orders")
    class PlaceTests {

        @Test
        @DisplayName("201 with the order body and a Location header")
        void placed() throws Exception {
            when(facade.placeOrder(any())).thenReturn(sampleOrder());

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customerId": 7, "type": "ESPRESSO"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/orders/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andExpect(jsonPath("$.priceTotal").value(2.50));
        }

        @Test
        @DisplayName("400 when a required field is missing")
        void missingField() throws Exception {
            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"type": "ESPRESSO"}"""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("404 when the customer is unknown")
        void unknownCustomer() throws Exception {
            when(facade.placeOrder(any())).thenThrow(new CustomerNotFoundException(9L));

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customerId": 9, "type": "ESPRESSO"}"""))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 when the shop is closed")
        void shopClosed() throws Exception {
            when(facade.placeOrder(any())).thenThrow(new ShopClosedException());

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customerId": 7, "type": "ESPRESSO"}"""))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("409 when the coffee is off the menu")
        void offMenu() throws Exception {
            when(facade.placeOrder(any())).thenThrow(new CoffeeNotOnMenuException(CoffeeType.LATTE));

            mvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customerId": 7, "type": "LATTE"}"""))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetTests {

        @Test
        @DisplayName("200 with the order body")
        void found() throws Exception {
            when(facade.getOrder(1L)).thenReturn(sampleOrder());

            mvc.perform(get("/api/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.coffee").value("Espresso"));
        }

        @Test
        @DisplayName("404 for an unknown id")
        void notFound() throws Exception {
            when(facade.getOrder(404L)).thenThrow(new OrderNotFoundException(404L));
            mvc.perform(get("/api/orders/404")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{id}/reorder")
    class ReorderTests {

        @Test
        @DisplayName("201 with the cloned order body")
        void reordered() throws Exception {
            Order clone = TestOrders.placedEspresso(2L, TestOrders.customer(7L));
            when(facade.reorder(1L)).thenReturn(clone);

            mvc.perform(post("/api/orders/1/reorder"))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/orders/2"))
                    .andExpect(jsonPath("$.id").value(2));
        }

        @Test
        @DisplayName("404 when the original order is unknown")
        void notFound() throws Exception {
            when(facade.reorder(404L)).thenThrow(new OrderNotFoundException(404L));
            mvc.perform(post("/api/orders/404/reorder")).andExpect(status().isNotFound());
        }
    }
}
