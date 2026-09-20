package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restart recovery, with the baristas stopped so what recovery does to the queue can be read
 * directly (the live end-to-end version is {@code OrderRecoveryPipelineTest}).
 */
@DisplayName("OrderRecovery")
class OrderRecoveryTest extends AbstractIntegrationTest {

    @Autowired OrderRecovery recovery;
    @Autowired OrderService orderService;
    @Autowired CoffeeShopFacade facade;

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));

    private Long seed(UserEntity customer, OrderStatus status) {
        Instant now = Instant.now();
        return orders.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(), status,
                LoyaltyTier.REGULAR, PRICE, now, now)).id();
    }

    private List<Long> drainQueue() throws InterruptedException {
        List<Long> ids = new java.util.ArrayList<>();
        Long id;
        while ((id = orderQueue.poll(0, TimeUnit.MILLISECONDS)) != null) {
            ids.add(id);
        }
        return ids;
    }

    @Nested
    @DisplayName("recover()")
    class RecoverTests {

        @Test
        @DisplayName("re-enqueues PLACED and PREPARING orders oldest first, and ignores READY, FULFILLED and CANCELLED")
        void enqueuesOnlyUnfinished() throws InterruptedException {
            UserEntity customer = customer("Alice");
            Long placed = seed(customer, OrderStatus.PLACED);
            seed(customer, OrderStatus.READY);
            Long preparing = seed(customer, OrderStatus.PREPARING);
            seed(customer, OrderStatus.FULFILLED);
            seed(customer, OrderStatus.CANCELLED);

            int recovered = recovery.recover();

            assertEquals(2, recovered);
            assertEquals(List.of(placed, preparing), drainQueue());
        }

        @Test
        @DisplayName("does nothing when there is nothing to recover")
        void nothingToRecover() throws InterruptedException {
            seed(customer("Alice"), OrderStatus.FULFILLED);

            assertEquals(0, recovery.recover());
            assertTrue(drainQueue().isEmpty());
        }

        @Test
        @DisplayName("publishes no events: no history row and no notification is created by recovery")
        void publishesNothing() {
            Long placed = seed(customer("Alice"), OrderStatus.PLACED);

            recovery.recover();

            assertEquals(0, history.count());
            assertTrue(notifications.notificationsFor(placed).isEmpty());
        }

        @Test
        @DisplayName("running twice does not queue an id twice")
        void idempotent() throws InterruptedException {
            Long placed = seed(customer("Alice"), OrderStatus.PLACED);

            recovery.recover();
            recovery.recover();

            assertEquals(List.of(placed), drainQueue());
        }

        @Test
        @DisplayName("an order that was freshly placed (already queued) and is also found by recovery is queued once")
        void alreadyQueuedIsNotDuplicated() throws InterruptedException {
            UserEntity customer = customer("Alice");
            Order fresh = facade.placeOrder(new PlaceOrderRequest(customer.id(), CoffeeType.ESPRESSO, List.of()));
            assertTrue(orderQueue.contains(fresh.id()));

            assertEquals(1, recovery.recover());

            assertEquals(List.of(fresh.id()), drainQueue());
        }
    }

    @Nested
    @DisplayName("wiring")
    class WiringTests {

        @Test
        @DisplayName("recover() is triggered by ApplicationReadyEvent (once per real startup), not by the supervisor's restartable lifecycle")
        void hookedOnApplicationReady() throws Exception {
            var method = OrderRecovery.class.getMethod("recover");
            EventListener listener = method.getAnnotation(EventListener.class);
            assertNotNull(listener);
            assertEquals(List.of(ApplicationReadyEvent.class), List.of(listener.value()));
        }

        @Test
        @DisplayName("rejects null collaborators")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> new OrderRecovery(null, orderQueue));
            assertThrows(NullPointerException.class, () -> new OrderRecovery(orderService, null));
        }
    }

    @Test
    @DisplayName("findUnfinishedOrderIds() returns PLACED and PREPARING ids only")
    void unfinishedIds() {
        UserEntity customer = customer("Alice");
        Long placed = seed(customer, OrderStatus.PLACED);
        seed(customer, OrderStatus.CANCELLED);

        assertEquals(List.of(placed), orderService.findUnfinishedOrderIds());
        assertFalse(orderService.findUnfinishedOrderIds().isEmpty());
    }
}
