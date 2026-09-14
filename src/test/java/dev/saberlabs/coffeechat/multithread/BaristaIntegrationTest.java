package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end proof that the Part 02 wiring actually works at runtime, not just in isolated unit
 * tests: placing an order through the real {@link CoffeeShopFacade} against a fully-started
 * Spring context reaches {@link OrderStatus#READY} on its own &mdash; via
 * {@code OrderStatusChangedEvent} &rarr; {@link OrderQueueDispatcher} &rarr; {@link OrderQueue}
 * &rarr; the {@link Barista} consumer loop(s) started by {@link BaristaSupervisor} on
 * {@code ApplicationReadyEvent} &mdash; with no test code calling {@code prepareOrder} directly.
 */
@SpringBootTest
@Testcontainers
class BaristaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    CoffeeShopFacade facade;

    @Autowired
    CustomerService customers;

    @Nested
    @DisplayName("the async Barista pipeline")
    class AsyncPipelineTests {

        @Test
        @DisplayName("a placed order reaches READY on its own, with no direct prepareOrder call")
        void placedOrderReachesReadyAsynchronously() throws InterruptedException {
            // Note: `placed` is the same mutable Order the Barista pipeline goes on to mutate
            // concurrently, so its status is not asserted here -- by the time this line runs it
            // may already have raced ahead past PLACED. awaitStatus() below is what actually
            // proves the pipeline: nothing in this test calls prepareOrder() directly.
            Customer customer = customers.create("Alice");
            Order placed = facade.placeOrder(new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of()));

            OrderStatus finalStatus = awaitStatus(placed.id(), OrderStatus.READY, 10_000);

            assertEquals(OrderStatus.READY, finalStatus);
        }

        private OrderStatus awaitStatus(Long orderId, OrderStatus expected, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            OrderStatus last = null;
            while (System.currentTimeMillis() < deadline) {
                last = facade.getOrder(orderId).status();
                if (last == expected) {
                    return last;
                }
                Thread.sleep(50);
            }
            fail("order " + orderId + " never reached " + expected + " (last seen: " + last + ")");
            return last;
        }
    }
}
