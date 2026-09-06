package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Pattern 5: OBSERVER &mdash; the event.
 *
 * <p>Published once per confirmed order-lifecycle transition. Spring's {@code @EventListener}
 * infrastructure <em>is</em> the observer registry, so there is no hand-rolled {@code Observable}
 * list beside it (PRD &sect;7.2).
 *
 * @param orderId    the order that changed
 * @param customerId the order's customer (for per-customer notification filtering)
 * @param from       the previous status, or {@code null} if the order was just placed
 * @param to         the new status
 * @param at         when the transition happened
 */
public record OrderStatusChangedEvent(Long orderId,
                                      Long customerId,
                                      OrderStatus from,
                                      OrderStatus to,
                                      Instant at) {

    public OrderStatusChangedEvent {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        Objects.requireNonNull(to, "to status cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
    }

    /**
     * Builds an event from an order that has <em>already</em> been moved to its new status.
     *
     * @param order the order, post-transition (its {@code status()} is the new one)
     * @param from  the status it held before the transition
     */
    public static OrderStatusChangedEvent of(Order order, OrderStatus from) {
        Objects.requireNonNull(order, "order cannot be null");
        return new OrderStatusChangedEvent(
                order.id(),
                order.customer().id(),
                from,
                order.status(),
                Instant.now());
    }
}
