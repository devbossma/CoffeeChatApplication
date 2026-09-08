package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;

/**
 * Pattern 6: COMMAND &mdash; cancel the order (legal from {@code PLACED}, {@code PREPARING} or
 * {@code READY}). {@code undo()} restores the status the order held before it was cancelled.
 */
public class CancelOrderCommand extends AbstractOrderCommand {

    private OrderStatus previousStatus;

    public CancelOrderCommand(Order order, OrderService orders, OrderEventPublisher events) {
        super(order, orders, events);
    }

    @Override
    public void execute() {
        this.previousStatus = order.status();
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
