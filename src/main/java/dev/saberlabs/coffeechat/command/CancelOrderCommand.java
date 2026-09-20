package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.service.StaffAccess;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

/**
 * Cancels an in-progress order. Known limitation: cancelling an order that has already been paid
 * has no refund flow (PRD &sect;4: no real payment processor), so the payment row is left as-is.
 */
public class CancelOrderCommand extends AbstractOrderCommand {

    private OrderStatus previousStatus;

    public CancelOrderCommand(@NotNull Long orderId,
                              @NotNull OrderService orders,
                              @NotNull OrderEventPublisher events,
                              @NotNull Actor actor,
                              @NotNull StaffAccess access) {
        super(orderId, orders, events, actor, access);
    }

    @Override
    public void execute() {
        authorize(StaffAccess.STAFF);
        this.previousStatus = orders.require(orderId).status();
        transition(OrderStatus.CANCELLED);
    }

    /**
     * Restores the status held before the cancellation, except PLACED: a restored PLACED order is
     * never re-queued (only a fresh placement is), so it would be stranded.
     */
    @Override
    public void undo() {
        if (previousStatus == OrderStatus.PLACED) {
            throw new UndoNotSupportedException("A cancelled PLACED order cannot be restored: it would never be queued again");
        }
        restore(previousStatus);
    }

    @Override
    public String name() {
        return "CancelOrder";
    }
}
