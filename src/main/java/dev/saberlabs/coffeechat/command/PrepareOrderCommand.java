package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import dev.saberlabs.coffeechat.template.CoffeePreparationTemplate;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Runs the Template Method recipe, then {@code PLACED -> PREPARING -> READY} in one transaction.
 *
 * <p>One transaction is fine here because the recipe is pure in-memory step logging (no waiting,
 * no I/O), so no row lock is held across real work. It also means a crash mid-command rolls back to
 * {@code PLACED}, so {@code PREPARING} is never committed by this command. It is still accepted as a
 * starting status (the {@code PLACED -> PREPARING} hop is skipped) so that restart recovery can
 * finish a {@code PREPARING} row created by any other means.
 */
public class PrepareOrderCommand extends AbstractOrderCommand {

    private final CoffeePreparationResolver preparations;
    private List<String> preparationLog = List.of();

    public PrepareOrderCommand(@NotNull Long orderId,
                               @NotNull OrderService orders,
                               @NotNull OrderEventPublisher events,
                               @NotNull CoffeePreparationResolver preparations) {
        super(orderId, orders, events);
        this.preparations = Objects.requireNonNull(preparations, "preparations cannot be null");
    }

    @Override
    public void execute() {
        OrderEntity order = orders.require(orderId);
        CoffeePreparationTemplate preparation = preparations.forType(order.baseCoffeeType());
        preparation.prepare();
        this.preparationLog = preparation.log();
        if (order.status() != OrderStatus.PREPARING) {
            transition(OrderStatus.PREPARING);
        }
        transition(OrderStatus.READY);
    }

    /**
     * Not supported: returning a prepared order to PLACED would put a finished order back in front of
     * the baristas' queue logic (and it is never re-queued), so it could be prepared twice or stranded.
     */
    @Override
    public void undo() {
        throw new UndoNotSupportedException("A prepared order cannot be returned to PLACED");
    }

    @Override
    public String name() {
        return "PrepareOrder";
    }

    /** The Template Method step log produced by the last {@link #execute()}. */
    public List<String> preparationLog() {
        return preparationLog;
    }
}
