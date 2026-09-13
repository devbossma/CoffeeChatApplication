package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Shared plumbing for the status-changing commands: apply a transition (persist + publish the
 * {@code OrderStatusChangedEvent} through the one publish point) and, for {@code undo()}, force
 * the status back.
 */
abstract class AbstractOrderCommand implements OrderCommand {

    protected final Order order;
    protected final OrderService orders;
    protected final OrderEventPublisher events;

    protected AbstractOrderCommand(@NotNull Order order, @NotNull OrderService orders, @NotNull OrderEventPublisher events) {
        this.order = Objects.requireNonNull(order, "order cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
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
