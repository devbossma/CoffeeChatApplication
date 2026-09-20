package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import dev.saberlabs.coffeechat.command.UndoNotSupportedException;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeNotOnMenuException;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.ShopClosedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.IllegalOrderTransitionException;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OrderController")
class OrderControllerTest extends AbstractWebMvcTest {

    private static final Actor CALLER = Actor.user(7L);



    private static Order sampleOrder(long id, OrderStatus status) {
        Instant now = Instant.now();
        return new Order(id, 7L, CoffeeType.ESPRESSO, List.<ExtraType>of(), "Espresso",
                PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO),
                LoyaltyTier.REGULAR, status, now, now);
    }

    private static Order sampleOrder() {
        return sampleOrder(1L, OrderStatus.PLACED);
    }

    /** A request as user 7. */
    private static MockHttpServletRequestBuilder as7(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", "7");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return as7(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    @DisplayName("POST /api/orders")
    class PlaceTests {

        @Test
        @DisplayName("201 with the order body and a Location header, placed as the caller")
        void placed() throws Exception {
            when(facade.placeOrder(any(PlaceOrderRequest.class), eq(CALLER))).thenReturn(sampleOrder());

            mvc.perform(json(post("/api/orders"), """
                            {"customerId": 7, "type": "ESPRESSO"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/orders/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andExpect(jsonPath("$.priceTotal").value(2.50));
        }

        @Test
        @DisplayName("401 without an X-User-Id header, and nothing is placed")
        void anonymous() throws Exception {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"customerId": 7, "type": "ESPRESSO"}"""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "X-User-Id"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("403 when a customer orders for someone else")
        void forOthers() throws Exception {
            when(facade.placeOrder(any(PlaceOrderRequest.class), any(Actor.class)))
                    .thenThrow(new RoleNotAllowedException(7L, Role.CUSTOMER, "place an order for customer 8"));

            mvc.perform(json(post("/api/orders"), """
                            {"customerId": 8, "type": "ESPRESSO"}"""))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 when a required field is missing, naming the field")
        void missingField() throws Exception {
            mvc.perform(json(post("/api/orders"), """
                            {"type": "ESPRESSO"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("customerId")));
        }

        @Test
        @DisplayName("400 for malformed JSON, an empty body, an unknown enum value and a wrongly typed field")
        void unreadableBodies() throws Exception {
            for (String body : new String[]{"{not json", "", "{\"customerId\": 7, \"type\": \"MOCHA\"}",
                    "{\"customerId\": \"seven\", \"type\": \"ESPRESSO\"}", "{\"customerId\": 7, \"type\": \"LATTE\", \"extras\": [\"CARAMEL\"]}"}) {
                mvc.perform(json(post("/api/orders"), body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            }
            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("400 when there is no body at all")
        void noBody() throws Exception {
            mvc.perform(as7(post("/api/orders"))).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("404 when the customer is unknown")
        void unknownCustomer() throws Exception {
            when(facade.placeOrder(any(PlaceOrderRequest.class), any(Actor.class))).thenThrow(new CustomerNotFoundException(9L));

            mvc.perform(json(post("/api/orders"), """
                            {"customerId": 9, "type": "ESPRESSO"}"""))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 when the shop is closed")
        void shopClosed() throws Exception {
            when(facade.placeOrder(any(PlaceOrderRequest.class), any(Actor.class))).thenThrow(new ShopClosedException());

            mvc.perform(json(post("/api/orders"), """
                            {"customerId": 7, "type": "ESPRESSO"}"""))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("409 when the coffee is off the menu")
        void offMenu() throws Exception {
            when(facade.placeOrder(any(PlaceOrderRequest.class), any(Actor.class))).thenThrow(new CoffeeNotOnMenuException(CoffeeType.LATTE));

            mvc.perform(json(post("/api/orders"), """
                            {"customerId": 7, "type": "LATTE"}"""))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetTests {

        @Test
        @DisplayName("200 with the order body, read as the caller")
        void found() throws Exception {
            when(facade.getOrder(1L, CALLER)).thenReturn(sampleOrder());

            mvc.perform(as7(get("/api/orders/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.coffee").value("Espresso"));
        }

        @Test
        @DisplayName("401 without identity: an anonymous read of an order by id is refused")
        void anonymous() throws Exception {
            mvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized());

            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("403 for another customer's order")
        void someoneElses() throws Exception {
            when(facade.getOrder(1L, CALLER)).thenThrow(new RoleNotAllowedException(7L, Role.CUSTOMER, "read order 1"));

            mvc.perform(as7(get("/api/orders/1"))).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("404 for an unknown id")
        void notFound() throws Exception {
            when(facade.getOrder(404L, CALLER)).thenThrow(new OrderNotFoundException(404L));

            mvc.perform(as7(get("/api/orders/404"))).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400, as a ProblemDetail, for a non-numeric id")
        void wrongTypedPathVariable() throws Exception {
            mvc.perform(as7(get("/api/orders/abc")))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{id}/reorder")
    class ReorderTests {

        @Test
        @DisplayName("201 with the clone and a Location header")
        void reordered() throws Exception {
            when(facade.reorder(1L, CALLER)).thenReturn(sampleOrder(2L, OrderStatus.PLACED));

            mvc.perform(as7(post("/api/orders/1/reorder")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/orders/2"));
        }

        @Test
        @DisplayName("401 without identity, 403 for someone else's order, 404 for an unknown one")
        void errors() throws Exception {
            when(facade.reorder(2L, CALLER)).thenThrow(new RoleNotAllowedException(7L, Role.CUSTOMER, "read order 2"));
            when(facade.reorder(3L, CALLER)).thenThrow(new OrderNotFoundException(3L));

            mvc.perform(post("/api/orders/1/reorder")).andExpect(status().isUnauthorized());
            mvc.perform(as7(post("/api/orders/2/reorder"))).andExpect(status().isForbidden());
            mvc.perform(as7(post("/api/orders/3/reorder"))).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 when a concurrent writer modified the order first (@Version conflict)")
        void concurrentModification() throws Exception {
            when(facade.reorder(1L, CALLER)).thenThrow(new ObjectOptimisticLockingFailureException(
                    dev.saberlabs.coffeechat.entity.OrderEntity.class, 1L));

            mvc.perform(as7(post("/api/orders/1/reorder"))).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("401 carries the reason in the body when the user id does not exist")
        void unknownActor() throws Exception {
            when(facade.reorder(1L, CALLER)).thenThrow(UnknownActorException.noSuchUser(987L));

            mvc.perform(as7(post("/api/orders/1/reorder")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("No user with id 987"));
        }

        @Test
        @DisplayName("409 when an undo is not supported, and 409 for a state conflict")
        void conflicts() throws Exception {
            when(facade.reorder(1L, CALLER)).thenThrow(new UndoNotSupportedException("no"));
            when(facade.reorder(2L, CALLER)).thenThrow(new OrderStateConflictException("unpaid"));

            mvc.perform(as7(post("/api/orders/1/reorder"))).andExpect(status().isConflict());
            mvc.perform(as7(post("/api/orders/2/reorder"))).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("staff transitions: prepare / fulfil / cancel")
    class TransitionTests {

        @Test
        @DisplayName("prepare, fulfil and cancel each call the facade as the caller and return the order")
        void transitions() throws Exception {
            when(facade.getOrder(1L, CALLER)).thenReturn(sampleOrder(1L, OrderStatus.READY));

            mvc.perform(as7(post("/api/orders/1/prepare"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
            mvc.perform(as7(post("/api/orders/1/fulfil"))).andExpect(status().isOk());
            mvc.perform(as7(post("/api/orders/1/cancel"))).andExpect(status().isOk());

            verify(facade).prepareOrder(1L, CALLER);
            verify(facade).fulfillOrder(1L, CALLER);
            verify(facade).cancelOrder(1L, CALLER);
        }

        @Test
        @DisplayName("401 without identity for each")
        void anonymous() throws Exception {
            for (String action : new String[]{"prepare", "fulfil", "cancel"}) {
                mvc.perform(post("/api/orders/1/" + action)).andExpect(status().isUnauthorized());
            }
            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("403 when the caller is not staff")
        void notStaff() throws Exception {
            RoleNotAllowedException denied = new RoleNotAllowedException(7L, Role.CUSTOMER, "perform this action");
            doThrow(denied).when(facade).prepareOrder(any(), any());
            doThrow(denied).when(facade).fulfillOrder(any(), any());
            doThrow(denied).when(facade).cancelOrder(any(), any());

            for (String action : new String[]{"prepare", "fulfil", "cancel"}) {
                mvc.perform(as7(post("/api/orders/1/" + action))).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("404 for an unknown order and 409 for an unpaid fulfil or an illegal transition")
        void notFoundAndConflicts() throws Exception {
            doThrow(new OrderNotFoundException(9L)).when(facade).prepareOrder(eq(9L), any());
            doThrow(new OrderStateConflictException("Order 1 has not been paid")).when(facade).fulfillOrder(eq(1L), any());

            mvc.perform(as7(post("/api/orders/9/prepare"))).andExpect(status().isNotFound());
            mvc.perform(as7(post("/api/orders/1/fulfil")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Order 1 has not been paid"));
        }

        @Test
        @DisplayName("an illegal transition (prepare a READY order, fulfil a PLACED one, cancel a FULFILLED one) is 409 with the reason, not 500")
        void illegalTransitions() throws Exception {
            doThrow(new IllegalOrderTransitionException(OrderStatus.READY, OrderStatus.READY)).when(facade).prepareOrder(eq(1L), any());
            doThrow(new IllegalOrderTransitionException(OrderStatus.PLACED, OrderStatus.FULFILLED)).when(facade).fulfillOrder(eq(2L), any());
            doThrow(new IllegalOrderTransitionException(OrderStatus.FULFILLED, OrderStatus.CANCELLED)).when(facade).cancelOrder(eq(3L), any());

            mvc.perform(as7(post("/api/orders/1/prepare")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Illegal order transition: READY -> READY"));
            mvc.perform(as7(post("/api/orders/2/fulfil"))).andExpect(status().isConflict());
            mvc.perform(as7(post("/api/orders/3/cancel"))).andExpect(status().isConflict());
        }

        @Test
        @DisplayName("there is no undo endpoint over REST")
        void noUndo() throws Exception {
            mvc.perform(as7(post("/api/orders/undo"))).andExpect(status().is4xxClientError());

            verify(facade, org.mockito.Mockito.never()).undoLastAction(any());
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{id}/pay")
    class PayTests {

        private PaymentResult result(PaymentStatus status, String detail) {
            return new PaymentResult(PaymentProvider.STRIPE, "1", new BigDecimal("2.50"), status, detail);
        }

        @Test
        @DisplayName("200 with the result when the payment succeeds")
        void paid() throws Exception {
            when(facade.payOrder(1L, PaymentProvider.STRIPE, CALLER)).thenReturn(result(PaymentStatus.PAID, "charged"));

            mvc.perform(json(post("/api/orders/1/pay"), """
                            {"provider": "STRIPE"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.provider").value("STRIPE"))
                    .andExpect(jsonPath("$.amount").value(2.50));
        }

        @Test
        @DisplayName("402 with the same result body when the gateway declines")
        void declined() throws Exception {
            when(facade.payOrder(1L, PaymentProvider.STRIPE, CALLER)).thenReturn(result(PaymentStatus.FAILED, "card declined"));

            mvc.perform(json(post("/api/orders/1/pay"), """
                            {"provider": "STRIPE"}"""))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.detail").value("card declined"));
        }

        @Test
        @DisplayName("400 for a missing, null or unknown provider, and for no body; the facade is never called")
        void badProvider() throws Exception {
            for (String body : new String[]{"{}", "{\"provider\": null}", "{\"provider\": \"BITCOIN\"}", "{\"provider\": \"stripe\"}", ""}) {
                mvc.perform(json(post("/api/orders/1/pay"), body)).andExpect(status().isBadRequest());
            }
            verifyNoInteractions(facade);
        }

        @Test
        @DisplayName("401 without identity, 403 for another customer, 404 for an unknown order")
        void identityAndOwnership() throws Exception {
            when(facade.payOrder(eq(2L), any(), eq(CALLER))).thenThrow(new RoleNotAllowedException(7L, Role.CUSTOMER, "pay order 2"));
            when(facade.payOrder(eq(3L), any(), eq(CALLER))).thenThrow(new OrderNotFoundException(3L));

            mvc.perform(post("/api/orders/1/pay").contentType(MediaType.APPLICATION_JSON).content("{\"provider\": \"CASH\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(json(post("/api/orders/2/pay"), "{\"provider\": \"CASH\"}")).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/orders/3/pay"), "{\"provider\": \"CASH\"}")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("409 when the order is not READY or is already paid")
        void conflicts() throws Exception {
            when(facade.payOrder(eq(1L), any(), any())).thenThrow(new OrderStateConflictException("Order 1 is not READY"));

            mvc.perform(json(post("/api/orders/1/pay"), "{\"provider\": \"PAYPAL\"}")).andExpect(status().isConflict());
        }
    }
}
