package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.service.StaffAccess;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.Set;

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
    protected final Actor actor;
    protected final StaffAccess access;

    /** What this command's status changes are recorded as {@code changed_by}: set by {@link #authorize}. */
    private Long recordedActorId;

    protected AbstractOrderCommand(@NotNull Long orderId,
                                   @NotNull OrderService orders,
                                   @NotNull OrderEventPublisher events,
                                   @NotNull Actor actor,
                                   @NotNull StaffAccess access) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
        this.access = Objects.requireNonNull(access, "access cannot be null");
    }

    /**
     * For a command that creates the order it then acts on ({@code PlaceOrderCommand}); {@link #orderId}
     * is assigned in {@code execute()}. Placing is a CUSTOMER action checked by the facade, not a staff
     * transition, so it carries no actor: {@code changed_by} stays NULL.
     */
    protected AbstractOrderCommand(@NotNull OrderService orders, @NotNull OrderEventPublisher events) {
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.actor = Actor.SYSTEM;
        this.access = null;
    }

    /**
     * Checks, inside this command's transaction and before anything is changed, that the actor may make
     * this transition, and remembers what to record as {@code changed_by} (a BARISTA's id, else NULL).
     */
    protected void authorize(Set<Role> allowed) {
        this.recordedActorId = access.authorize(actor, allowed);
    }

    @Override
    public Long actorUserId() {
        return recordedActorId;
    }

    @Override
    public void attributeUndoTo(Long userId) {
        this.recordedActorId = userId;
    }

    /** Legal forward move: apply it to the managed entity, then publish {@code from -> target}. */
    protected void transition(OrderStatus target) {
        OrderEntity order = orders.require(orderId);
        OrderStatus from = order.status();
        order.transitionTo(target);
        events.publishStatusChange(order, from, actorUserId());
    }

    /** undo-only reverse move: force the status back, then publish {@code from -> back}. */
    protected void restore(OrderStatus back) {
        OrderEntity order = orders.require(orderId);
        OrderStatus from = order.status();
        order.restoreStatus(back);
        events.publishStatusChange(order, from, actorUserId());
    }
}
