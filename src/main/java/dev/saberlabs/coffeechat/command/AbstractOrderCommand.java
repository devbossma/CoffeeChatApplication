package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;

/**
 * Shared plumbing for the status-changing commands: apply a transition (persist + publish the
 * {@code OrderStatusChangedEvent} through the one publish point) and, for {@code undo()}, force
 * the status back.
 */
abstract class AbstractOrderCommand implements OrderCommand {

    protected final Order order;
    protected final OrderService orders;
    protected final OrderEventPublisher events;

    protected AbstractOrderCommand(Order order, OrderService orders, OrderEventPublisher events) {
        this.order = order;
        this.orders = orders;
        this.events = events;
    }

    /** Legal forward move: {@code order.transitionTo(target)}, persist, then publish {@code from -> target}. */
    protected void transition(OrderStatus target) {
        OrderStatus from = order.status();
        order.transitionTo(target);
        orders.save(order);
        events.publishStatusChange(order, from);
    }

    /** undo-only reverse move: force the status back, persist, then publish {@code from -> back}. */
    protected void restore(OrderStatus back) {
        OrderStatus from = order.status();
        order.restoreStatus(back);
        orders.save(order);
        events.publishStatusChange(order, from);
    }
}
