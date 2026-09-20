package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Inserts a new order at {@code PLACED} and publishes {@code null -> PLACED}. The tier and price
 * arrive already derived (by the facade's Strategy/Decorator pipeline) and are frozen into the row.
 */
public class PlaceOrderCommand extends AbstractOrderCommand {

    private final Long customerId;
    private final CoffeeType type;
    private final List<ExtraType> extras;
    private final LoyaltyTier appliedTier;
    private final PriceBreakdown price;
    private final CustomerService customers;

    public PlaceOrderCommand(@NotNull Long customerId,
                             @NotNull CoffeeType type,
                             @NotNull List<ExtraType> extras,
                             @NotNull LoyaltyTier appliedTier,
                             @NotNull PriceBreakdown price,
                             @NotNull CustomerService customers,
                             @NotNull OrderService orders,
                             @NotNull OrderEventPublisher events) {
        super(orders, events);
        this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.extras = List.copyOf(Objects.requireNonNull(extras, "extras cannot be null"));
        this.appliedTier = Objects.requireNonNull(appliedTier, "appliedTier cannot be null");
        this.price = Objects.requireNonNull(price, "price cannot be null");
        this.customers = Objects.requireNonNull(customers, "customers cannot be null");
    }

    @Override
    public void execute() {
        OrderEntity order = orders.create(customers.require(customerId), type, extras, appliedTier, price);
        this.orderId = order.id();
        events.publishStatusChange(order, null, actorUserId());
    }

    @Override
    public void undo() {
        restore(OrderStatus.CANCELLED);
    }

    @Override
    public String name() {
        return "PlaceOrder";
    }

    /** The id of the order created by the last successful {@link #execute()}, or {@code null}. */
    public Long orderId() {
        return orderId;
    }
}
