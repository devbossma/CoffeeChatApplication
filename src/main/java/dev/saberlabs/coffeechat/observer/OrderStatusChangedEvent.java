package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderEntity;
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
 * @param actorUserId the user who caused it, or {@code null} for an automated/system transition.
 *                   Step 3 always passes {@code null}; Part 03 Step 4 supplies a BARISTA here
 *                   (see {@code OrderCommand#actorUserId()}), which the history listener maps to
 *                   {@code order_status_history.changed_by}
 */
public record OrderStatusChangedEvent(Long orderId,
                                      Long customerId,
                                      OrderStatus from,
                                      OrderStatus to,
                                      Instant at,
                                      Long actorUserId) {

    /** A system (actor-less) transition. */
    public OrderStatusChangedEvent(Long orderId, Long customerId, OrderStatus from, OrderStatus to, Instant at) {
        this(orderId, customerId, from, to, at, null);
    }

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
     * @param actorUserId the user who caused it, or {@code null} for a system transition
     */
    public static OrderStatusChangedEvent of(OrderEntity order, OrderStatus from, Long actorUserId) {
        Objects.requireNonNull(order, "order cannot be null");
        return new OrderStatusChangedEvent(
                order.id(),
                order.customerId(),
                from,
                order.status(),
                Instant.now(),
                actorUserId);
    }
}
