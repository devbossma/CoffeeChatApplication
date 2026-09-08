package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderService")
class OrderServiceTest {

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService();
    }

    private static Order orderFor(Customer customer) {
        return new Order(
                customer,
                new Espresso(),
                CoffeeType.ESPRESSO,
                List.of(),
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR);
    }

    private static Customer customer(long id) {
        Customer c = new Customer("Customer " + id);
        c.assignId(id);
        return c;
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("assigns a generated id to an order that has none")
        void assignsId() {
            Order saved = orderService.save(orderFor(customer(1L)));
            assertNotNull(saved.id());
        }

        @Test
        @DisplayName("assigns increasing ids across saves")
        void idsIncrease() {
            Long first = orderService.save(orderFor(customer(1L))).id();
            Long second = orderService.save(orderFor(customer(1L))).id();
            assertTrue(second > first);
        }

        @Test
        @DisplayName("returns the same instance it was given")
        void returnsSameInstance() {
            Order order = orderFor(customer(1L));
            assertSame(order, orderService.save(order));
        }

        @Test
        @DisplayName("keeps an already-assigned id instead of regenerating one")
        void keepsExistingId() {
            Order order = orderFor(customer(1L));
            order.assignId(99L);
            orderService.save(order);
            assertEquals(99L, orderService.findById(99L).orElseThrow().id());
        }

        @Test
        @DisplayName("a saved order is retrievable by its id")
        void savedIsFindable() {
            Order saved = orderService.save(orderFor(customer(1L)));
            assertSame(saved, orderService.findById(saved.id()).orElseThrow());
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("returns the order when it exists")
        void findsExisting() {
            Order saved = orderService.save(orderFor(customer(1L)));
            assertTrue(orderService.findById(saved.id()).isPresent());
        }

        @Test
        @DisplayName("returns empty for an unknown id")
        void emptyForUnknown() {
            assertTrue(orderService.findById(404L).isEmpty());
        }
    }

    @Nested
    @DisplayName("findByCustomer()")
    class FindByCustomerTests {

        @Test
        @DisplayName("returns only the orders belonging to that customer")
        void filtersByCustomer() {
            orderService.save(orderFor(customer(1L)));
            orderService.save(orderFor(customer(1L)));
            orderService.save(orderFor(customer(2L)));
            assertEquals(2, orderService.findByCustomer(1L).size());
        }

        @Test
        @DisplayName("returns an empty list when the customer has no orders")
        void emptyForCustomerWithNoOrders() {
            orderService.save(orderFor(customer(1L)));
            assertTrue(orderService.findByCustomer(2L).isEmpty());
        }
    }

    @Nested
    @DisplayName("clear()")
    class ClearTests {

        @Test
        @DisplayName("removes every stored order")
        void removesAll() {
            orderService.save(orderFor(customer(1L)));
            orderService.clear();
            assertTrue(orderService.findByCustomer(1L).isEmpty());
        }

        @Test
        @DisplayName("resets the id sequence")
        void resetsSequence() {
            orderService.save(orderFor(customer(1L)));
            orderService.clear();
            assertEquals(1L, orderService.save(orderFor(customer(1L))).id());
        }

        @Test
        @DisplayName("a cleared store no longer finds a previously saved order")
        void previouslySavedGone() {
            Order saved = orderService.save(orderFor(customer(1L)));
            Long id = saved.id();
            orderService.clear();
            assertFalse(orderService.findById(id).isPresent());
        }
    }
}
