package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;

/**
 * Pattern 6: COMMAND &mdash; fulfil the order: {@code READY -> FULFILLED}, and bump the
 * customer's fulfilled-order count (which may raise their derived loyalty tier for the
 * <em>next</em> order &mdash; never retroactively). {@code undo()} reverts the status only; the
 * count bump is not reversed, matching {@code MyDesignPattern}.
 */
public class FulfillOrderCommand extends AbstractOrderCommand {

    private final CustomerService customers;

    public FulfillOrderCommand(Order order,
                               OrderService orders,
                               OrderEventPublisher events,
                               CustomerService customers) {
        super(order, orders, events);
        this.customers = customers;
    }

    @Override
    public void execute() {
        transition(OrderStatus.FULFILLED);
        customers.incrementFulfilled(order.customer().id());
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
