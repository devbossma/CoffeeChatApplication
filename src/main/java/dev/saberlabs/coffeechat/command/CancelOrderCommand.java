package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

/**
 * Cancels an in-progress order. Known limitation: cancelling an order that has already been paid
 * has no refund flow (PRD &sect;4: no real payment processor), so the payment row is left as-is.
 */
public class CancelOrderCommand extends AbstractOrderCommand {

    private OrderStatus previousStatus;

    public CancelOrderCommand(@NotNull Long orderId, @NotNull OrderService orders, @NotNull OrderEventPublisher events) {
        super(orderId, orders, events);
    }

    @Override
    public void execute() {
        this.previousStatus = orders.require(orderId).status();
        transition(OrderStatus.CANCELLED);
    }

    @Override
    public void undo() {
        restore(previousStatus);
    }

    @Override
    public String name() {
        return "CancelOrder";
    }
}
