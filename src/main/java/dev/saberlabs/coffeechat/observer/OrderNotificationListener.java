package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pattern 5: OBSERVER &mdash; the single listener.
 *
 * <p>Replaces {@code MyDesignPattern}'s {@code Customer implements OrderObserver} + hand-rolled
 * observer list. It turns each {@link OrderStatusChangedEvent} into a customer-facing
 * notification: logged, and kept in a small in-memory per-order list so callers (and tests) can
 * read what a customer would have been told. Part 03 adds durable persistence of this trail; the
 * async Barista must <em>not</em> fire its own duplicate notification ({@code CLAUDE.md}).
 */
@Component
public class OrderNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    private final Map<Long, List<String>> notificationsByOrder = new ConcurrentHashMap<>();

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        String message = messageFor(event.orderId(), event.to());
        notificationsByOrder
                .computeIfAbsent(event.orderId(), id -> new CopyOnWriteArrayList<>())
                .add(message);
        log.info("[notification] customer {} — {}", event.customerId(), message);
    }

    /** Every notification raised for an order so far, oldest first. */
    public List<String> notificationsFor(Long orderId) {
        return List.copyOf(notificationsByOrder.getOrDefault(orderId, List.of()));
    }

    /** The most recent notification for an order, or {@code null} if there has been none. */
    public String latestFor(Long orderId) {
        List<String> all = notificationsByOrder.get(orderId);
        return (all == null || all.isEmpty()) ? null : all.get(all.size() - 1);
    }

    /** Test/reset aid. */
    public void clear() {
        notificationsByOrder.clear();
    }

    private static String messageFor(Long orderId, OrderStatus status) {
        return switch (status) {
            case PLACED -> "Order " + orderId + " has been placed";
            case PREPARING -> "Order " + orderId + " is being prepared";
            case READY -> "Order " + orderId + " is ready for pickup";
            case FULFILLED -> "Order " + orderId + " is complete — enjoy your coffee";
            case CANCELLED -> "Order " + orderId + " has been cancelled";
        };
    }
}
