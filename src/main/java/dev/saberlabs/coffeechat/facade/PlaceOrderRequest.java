package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;

import java.util.List;
import java.util.Objects;

/**
 * What {@link CoffeeShopFacade#placeOrder(PlaceOrderRequest)} needs: who is ordering, which base
 * coffee, and which extras. The REST layer maps its HTTP body to this; it is not itself a web DTO.
 *
 * @param customerId an existing customer's id
 * @param type       the base coffee type
 * @param extras     extras to apply in order; may be empty, never null
 */
public record PlaceOrderRequest(Long customerId, CoffeeType type, List<ExtraType> extras) {

    public PlaceOrderRequest {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        extras = List.copyOf(Objects.requireNonNull(extras, "extras cannot be null"));
    }
}
