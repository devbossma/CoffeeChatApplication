package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import dev.saberlabs.coffeechat.template.CoffeePreparationTemplate;

import java.util.List;

/**
 * Pattern 6: COMMAND &mdash; prepare the order: run the Template Method recipe for its coffee
 * type, then move {@code PLACED -> PREPARING -> READY}. {@code undo()} sends it back to
 * {@code PLACED}.
 *
 * <p>Part 02 splits this into two async barista steps (mark-preparing, mark-ready); here the two
 * transitions happen together so the pattern has a single "prepare" action to expose now.
 */
public class PrepareOrderCommand extends AbstractOrderCommand {

    private final CoffeePreparationResolver preparations;
    private List<String> preparationLog = List.of();

    public PrepareOrderCommand(Order order,
                               OrderService orders,
                               OrderEventPublisher events,
                               CoffeePreparationResolver preparations) {
        super(order, orders, events);
        this.preparations = preparations;
    }

    @Override
    public void execute() {
        CoffeePreparationTemplate preparation = preparations.forType(order.baseType());
        preparation.prepare();
        this.preparationLog = preparation.log();
        transition(OrderStatus.PREPARING);
        transition(OrderStatus.READY);
    }

    @Override
    public void undo() {
        restore(OrderStatus.PLACED);
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
