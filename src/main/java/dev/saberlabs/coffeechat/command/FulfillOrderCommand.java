package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * {@code READY -> FULFILLED}, then one more fulfilled order for the customer, in one transaction.
 *
 * <p>Order matters. The status change is flushed <em>first</em> so the {@code @Version} check fires
 * before the increment, and the increment only runs once the transition has been accepted: a second
 * fulfilment of the same order fails the legality check (and a concurrent one fails the version
 * check) before it can count twice. The increment is an atomic SQL update, not a read-modify-write.
 */
public class FulfillOrderCommand extends AbstractOrderCommand {

    private final UserRepository users;

    public FulfillOrderCommand(@NotNull Long orderId,
                               @NotNull OrderService orders,
                               @NotNull OrderEventPublisher events,
                               @NotNull UserRepository users) {
        super(orderId, orders, events);
        this.users = Objects.requireNonNull(users, "users cannot be null");
    }

    @Override
    public void execute() {
        transition(OrderStatus.FULFILLED);
        orders.flush();
        users.incrementFulfilledOrders(orders.require(orderId).customer().id());
    }

    @Override
    public void undo() {
        restore(OrderStatus.READY);
    }

    @Override
    public String name() {
        return "FulfillOrder";
    }
}
