package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;

/**
 * Pattern 6: COMMAND &mdash; place the order: {@code null -> PLACED}, persist, notify.
 * {@code undo()} cancels it.
 */
public class PlaceOrderCommand extends AbstractOrderCommand {

    public PlaceOrderCommand(Order order, OrderService orders, OrderEventPublisher events) {
        super(order, orders, events);
    }

    @Override
    public void execute() {
        transition(OrderStatus.PLACED);
    }

    @Override
    public void undo() {
        restore(OrderStatus.CANCELLED);
    }

    @Override
    public String name() {
        return "PlaceOrder";
    }
}
