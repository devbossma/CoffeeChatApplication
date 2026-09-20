package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderStatusChangedEvent;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Pattern: PRODUCER-CONSUMER (Part 02) &mdash; the producer side. Turns "an order reached PLACED"
 * into "its id is on the {@link OrderQueue}".
 *
 * <p><b>AFTER_COMMIT, deliberately.</b> The publishing command is still inside its transaction when
 * the event is raised; enqueuing then would let a barista dequeue an id whose row is not yet
 * visible (or is about to roll back). {@code AFTER_COMMIT} fires only once the row is durable, so a
 * barista can never dequeue an uncommitted order.
 *
 * <p><b>Do not touch the database from this method.</b> It runs after the transaction has
 * committed, so a repository call here either fails with "no transaction" or silently does nothing
 * useful. If a future listener in this phase must write, it needs its own
 * {@code @Transactional(propagation = REQUIRES_NEW)}. This one only enqueues an id.
 *
 * <p>{@code @TransactionalEventListener} <em>drops</em> the event when there is no active
 * transaction at publish time; {@code OrderEventPublisher} therefore refuses to publish outside
 * one, so that drop cannot happen silently.
 */
@Component
public class OrderQueueDispatcher {

    private final OrderQueue orderQueue;

    public OrderQueueDispatcher(@NotNull OrderQueue orderQueue) {
        this.orderQueue = Objects.requireNonNull(orderQueue, "orderQueue cannot be null");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.to() != OrderStatus.PLACED) {
            return;
        }
        orderQueue.enqueue(event.orderId());
    }
}
