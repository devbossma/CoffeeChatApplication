package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Pattern 5: OBSERVER &mdash; the single publish point.
 *
 * <p>The order-lifecycle commands call this after a transition has been applied and confirmed;
 * nothing else publishes an {@link OrderStatusChangedEvent}. Keeping one publisher (rather than
 * every command touching {@link ApplicationEventPublisher} directly) is what makes "one publish
 * point, one listener" ({@code CLAUDE.md}) enforceable.
 */
@Component
public class OrderEventPublisher {

    private final ApplicationEventPublisher publisher;

    public OrderEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publishes the transition {@code from -> order.status()} for {@code order}.
     *
     * @param order the order, already moved to its new status
     * @param from  the status it held before
     */
    public void publishStatusChange(Order order, OrderStatus from) {
        publisher.publishEvent(OrderStatusChangedEvent.of(order, from));
    }
}
