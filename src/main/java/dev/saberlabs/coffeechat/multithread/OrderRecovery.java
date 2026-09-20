package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Restart recovery. {@link OrderQueue} is in memory, so after a process restart it is empty while
 * PLACED (and, defensively, PREPARING) rows from before the restart still exist; this re-enqueues
 * their ids so they are not stranded.
 *
 * <p><b>Where it hooks in:</b> {@code ApplicationReadyEvent}, which fires once per real startup. It
 * is deliberately <em>not</em> tied to {@code BaristaSupervisor}'s lifecycle: the supervisor is a
 * {@code SmartLifecycle} that also restarts when a paused (test) context is resumed, and recovering
 * then would re-enqueue orders that are already in this same JVM's queue. Baristas auto-start at
 * context refresh, before this event, so the consumers are already draining the queue.
 *
 * <p><b>No double processing.</b> Recovery only enqueues ids and publishes no events, so it sends no
 * duplicate notification. A live placement can race it (the web server is up before this event
 * fires), so an id may be both recovered and freshly queued: the queue ignores an id that is already
 * waiting (efficiency), and the consumer is idempotent (correctness) &mdash; preparing an order that
 * is no longer PLACED/PREPARING is a quiet skip, guarded by the transition rules and {@code @Version}.
 *
 * <p>The lifecycle transitions themselves still go only through {@code CoffeeShopFacade}; this class
 * only reads ids and enqueues them.
 */
@Component
public class OrderRecovery {

    private static final Logger log = LoggerFactory.getLogger(OrderRecovery.class);

    private final OrderService orders;
    private final OrderQueue orderQueue;

    public OrderRecovery(@NotNull OrderService orders, @NotNull OrderQueue orderQueue) {
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.orderQueue = Objects.requireNonNull(orderQueue, "orderQueue cannot be null");
    }

    /** @return the number of unfinished orders found and offered to the queue */
    @EventListener(ApplicationReadyEvent.class)
    public int recover() {
        List<Long> ids = orders.findUnfinishedOrderIds();
        for (Long id : ids) {
            orderQueue.enqueue(id);
        }
        if (!ids.isEmpty()) {
            log.info("Recovered {} unfinished order(s) after startup: {}", ids.size(), ids);
        }
        return ids.size();
    }
}
