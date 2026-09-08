package dev.saberlabs.coffeechat.prototype;

import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.Order;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Pattern 9: PROTOTYPE.
 *
 * <p>The genuine change from {@code MyDesignPattern}, where cloning was a hand-written
 * {@code cloneOrder()} copy method: here it is an actual Spring <em>prototype-scoped</em> bean.
 * {@code CoffeeShopFacade.reorder(orderId)} asks {@code ObjectProvider<OrderPrototype>} for a
 * fresh, independent instance, seeds it from the original order's <em>structure only</em> (base
 * coffee type + extras + customer &mdash; never id, status or timestamps), and re-places it. The
 * clone is not a second persistence path: it goes back through {@code placeOrder}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OrderPrototype {

    private Long customerId;
    private CoffeeType type;
    private List<ExtraType> extras = List.of();

    /**
     * Seeds this prototype from an existing order, copying only what a re-order needs to
     * reconstruct an equivalent order.
     */
    public void copyOf(Order original) {
        Objects.requireNonNull(original, "original order cannot be null");
        this.customerId = original.customer().id();
        this.type = original.baseType();
        this.extras = List.copyOf(original.extras());
    }

    /**
     * @return a {@link PlaceOrderRequest} equivalent to the seeded order
     * @throws IllegalStateException if {@link #copyOf(Order)} has not been called
     */
    public PlaceOrderRequest toPlaceOrderRequest() {
        if (type == null) {
            throw new IllegalStateException("OrderPrototype has not been seeded from an order");
        }
        return new PlaceOrderRequest(customerId, type, extras);
    }

    public Long customerId() {
        return customerId;
    }

    public CoffeeType type() {
        return type;
    }

    public List<ExtraType> extras() {
        return extras;
    }
}
