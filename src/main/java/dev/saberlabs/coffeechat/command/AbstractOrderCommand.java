package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Shared shape of the order-lifecycle commands: hold an order <em>id</em> (never an object), load
 * the managed {@link OrderEntity} inside {@link #execute()} &mdash; which {@code OrderInvoker} runs in
 * exactly one transaction &mdash; mutate it, and publish the change. Loading and mutating in one
 * persistence context is what lets the {@code @Version} check arbitrate two concurrent transitions.
 */
abstract class AbstractOrderCommand implements OrderCommand {

    /** Fixed at construction, except for {@code PlaceOrderCommand}, which learns it on execute. */
    protected Long orderId;
    protected final OrderService orders;
    protected final OrderEventPublisher events;

    protected AbstractOrderCommand(@NotNull Long orderId, @NotNull OrderService orders, @NotNull OrderEventPublisher events) {
        this(orders, events);
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
    }

    /** For a command that creates the order it then acts on; {@link #orderId} is assigned in {@code execute()}. */
    protected AbstractOrderCommand(@NotNull OrderService orders, @NotNull OrderEventPublisher events) {
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
    }

    /** Legal forward move: apply it to the managed entity, then publish {@code from -> target}. */
    protected void transition(OrderStatus target) {
        OrderEntity order = orders.require(orderId);
        OrderStatus from = order.status();
        order.transitionTo(target);
        events.publishStatusChange(order, from);
    }

    /** undo-only reverse move: force the status back, then publish {@code from -> back}. */
    protected void restore(OrderStatus back) {
        OrderEntity order = orders.require(orderId);
        OrderStatus from = order.status();
        order.restoreStatus(back);
        events.publishStatusChange(order, from);
    }
}
