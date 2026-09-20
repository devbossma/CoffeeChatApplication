package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pattern 5: OBSERVER &mdash; the single publish point.
 *
 * <p>The order-lifecycle commands call this after a transition has been applied; nothing else
 * publishes an {@link OrderStatusChangedEvent}. One event, two kinds of listener, and the phase of
 * each is what keeps the audit and the side effects honest:
 * <ul>
 *   <li>{@code OrderStatusHistoryListener} is a plain, synchronous listener with
 *       {@code MANDATORY} propagation: it runs <em>inside</em> the publishing transaction, so the
 *       audit row commits or rolls back together with the status change and can never disagree
 *       with it.</li>
 *   <li>{@code OrderNotificationListener} and {@code OrderQueueDispatcher} are
 *       {@code AFTER_COMMIT}: a customer is never told about, and a barista never handed, a
 *       change that did not commit.</li>
 * </ul>
 *
 * <p><b>This refuses to publish outside a transaction.</b> {@code @TransactionalEventListener}
 * silently <em>drops</em> an event that is published with no active transaction (unless a listener
 * opts into {@code fallbackExecution}), which for the queue dispatcher would mean a committed order
 * that is simply never enqueued, with nothing logged. Failing loudly here turns that silent loss
 * into an immediate error.
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
     * @param order       the order, already moved to its new status
     * @param from        the status it held before ({@code null} for a just-placed order)
     * @param actorUserId the user responsible, or {@code null} for a system transition
     * @throws IllegalStateException if no transaction is active
     */
    public void publishStatusChange(OrderEntity order, OrderStatus from, Long actorUserId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "An order status change must be published inside a transaction: AFTER_COMMIT listeners "
                            + "would silently drop it, and the audit row must join the status change's transaction");
        }
        publisher.publishEvent(OrderStatusChangedEvent.of(order, from, actorUserId));
    }
}
