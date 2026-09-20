package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end proof that the Part 02 wiring works at runtime against the real database: placing an
 * order through the real {@link CoffeeShopFacade} reaches {@link OrderStatus#READY} on its own
 * &mdash; via {@code OrderStatusChangedEvent} (AFTER_COMMIT) &rarr; {@link OrderQueueDispatcher}
 * &rarr; {@link OrderQueue} &rarr; the {@link Barista} consumer loop(s) started by
 * {@link BaristaSupervisor} &mdash; with no test code calling {@code prepareOrder} directly.
 */
@DisplayName("Barista pipeline (integration)")
class BaristaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    CoffeeShopFacade facade;

    @Override
    protected boolean baristasLive() {
        return true;
    }

    @Nested
    @DisplayName("the async Barista pipeline")
    class AsyncPipelineTests {

        @Test
        @DisplayName("a placed order reaches READY on its own, with no direct prepareOrder call")
        void placedOrderReachesReadyAsynchronously() {
            UserEntity customer = customer("Alice");
            Order placed = facade.placeOrder(new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of()));

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertEquals(OrderStatus.READY, facade.getOrder(placed.id()).status()));
        }

        @Test
        @DisplayName("several orders are all prepared, each exactly once")
        void manyOrdersEachPreparedOnce() {
            UserEntity customer = customer("Alice");
            List<Long> ids = java.util.stream.IntStream.range(0, 6)
                    .mapToObj(i -> facade.placeOrder(
                            new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of())).id())
                    .toList();

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                for (Long id : ids) {
                    assertEquals(OrderStatus.READY, facade.getOrder(id).status());
                }
            });
        }
    }
}
