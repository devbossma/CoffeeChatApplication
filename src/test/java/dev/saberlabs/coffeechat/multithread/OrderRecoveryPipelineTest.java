package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Recovery end to end with live baristas: what a restart leaves behind is finished, exactly once,
 * with a truthful audit trail.
 */
@DisplayName("Restart recovery (live pipeline)")
class OrderRecoveryPipelineTest extends AbstractIntegrationTest {

    @Autowired OrderRecovery recovery;

    private static final PriceBreakdown PRICE =
            PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));

    @Override
    protected boolean baristasLive() {
        return true;
    }

    private Long seed(UserEntity customer, OrderStatus status) {
        Instant now = Instant.now();
        return orders.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(), status,
                LoyaltyTier.REGULAR, PRICE, now, now)).id();
    }

    private OrderStatus statusOf(Long id) {
        return orders.findById(id).orElseThrow().status();
    }

    @Test
    @DisplayName("orders left PLACED and PREPARING by an interrupted run both reach READY, each with only the transitions it actually made")
    void interruptedOrdersAreFinished() {
        UserEntity customer = customer("Alice");
        Long placed = seed(customer, OrderStatus.PLACED);
        Long preparing = seed(customer, OrderStatus.PREPARING);
        Long fulfilled = seed(customer, OrderStatus.FULFILLED);

        recovery.recover();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertEquals(OrderStatus.READY, statusOf(placed));
            assertEquals(OrderStatus.READY, statusOf(preparing));
        });
        assertEquals(OrderStatus.FULFILLED, statusOf(fulfilled), "a finished order is never touched");

        List<OrderStatusHistoryEntity> placedTrail = history.findByOrderIdOrderByChangedAtAscIdAsc(placed);
        assertEquals(List.of(OrderStatus.PREPARING, OrderStatus.READY),
                placedTrail.stream().map(OrderStatusHistoryEntity::toStatus).toList());
        List<OrderStatusHistoryEntity> preparingTrail = history.findByOrderIdOrderByChangedAtAscIdAsc(preparing);
        assertEquals(List.of(OrderStatus.READY),
                preparingTrail.stream().map(OrderStatusHistoryEntity::toStatus).toList(),
                "a PREPARING order only makes the PREPARING -> READY hop");
        assertEquals(0, history.findByOrderIdOrderByChangedAtAscIdAsc(fulfilled).size());
    }

    @Test
    @DisplayName("recovering again after they are done changes nothing: no duplicate history rows")
    void secondRecoveryIsHarmless() {
        UserEntity customer = customer("Alice");
        Long placed = seed(customer, OrderStatus.PLACED);
        recovery.recover();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(OrderStatus.READY, statusOf(placed)));
        long rowsBefore = history.count();

        recovery.recover();
        // an order that is already READY is not unfinished, so nothing is queued and nothing is written
        await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(rowsBefore, history.count()));
        assertEquals(OrderStatus.READY, statusOf(placed));
    }

    @Test
    @DisplayName("an id delivered to a barista for an order that is already prepared is skipped, not re-prepared")
    void staleIdIsSkipped() {
        UserEntity customer = customer("Alice");
        Long placed = seed(customer, OrderStatus.PLACED);
        recovery.recover();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(OrderStatus.READY, statusOf(placed)));
        long rowsBefore = history.count();

        orderQueue.enqueue(placed); // a stale duplicate reaching a barista

        await().atMost(Duration.ofSeconds(5)).until(orderQueue::isEmpty);
        await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(rowsBefore, history.count()));
        assertEquals(OrderStatus.READY, statusOf(placed));
    }
}
