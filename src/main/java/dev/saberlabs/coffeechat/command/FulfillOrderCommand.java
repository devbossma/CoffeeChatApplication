package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.service.PaymentService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * {@code READY -> FULFILLED}, then one more fulfilled order for the customer, in one transaction.
 * The order must be PAID: loyalty tiers derive from {@code fulfilled_orders}, so an unpaid
 * fulfilment would earn the customer future discounts for free.
 *
 * <p>Order matters. The status change is flushed <em>first</em> so the {@code @Version} check fires
 * before the increment, and the increment only runs once the transition has been accepted: a second
 * fulfilment of the same order fails the legality check (and a concurrent one fails the version
 * check) before it can count twice. The increment is an atomic SQL update, not a read-modify-write.
 */
public class FulfillOrderCommand extends AbstractOrderCommand {

    private final UserRepository users;
    private final PaymentService payments;

    public FulfillOrderCommand(@NotNull Long orderId,
                               @NotNull OrderService orders,
                               @NotNull OrderEventPublisher events,
                               @NotNull UserRepository users,
                               @NotNull PaymentService payments) {
        super(orderId, orders, events);
        this.users = Objects.requireNonNull(users, "users cannot be null");
        this.payments = Objects.requireNonNull(payments, "payments cannot be null");
    }

    @Override
    public void execute() {
        OrderEntity order = orders.require(orderId);
        if (order.status() == OrderStatus.READY) {
            // Checked before the transition and the increment, inside the same transaction. A status
            // other than READY falls through to transitionTo's own illegal-transition failure.
            payments.requirePaid(orderId);
        }
        transition(OrderStatus.FULFILLED);
        orders.flush();
        users.incrementFulfilledOrders(orders.require(orderId).customerId());
    }

    /**
     * Not supported: fulfilling also incremented the customer's {@code fulfilled_orders}, which
     * feeds loyalty tiers. Restoring the status alone would let a re-fulfilment count twice.
     */
    @Override
    public void undo() {
        throw new UndoNotSupportedException("A fulfilment cannot be undone: it has already counted toward the customer's loyalty tier");
    }

    @Override
    public boolean undoable() {
        return false;
    }

    @Override
    public String name() {
        return "FulfillOrder";
    }
}
