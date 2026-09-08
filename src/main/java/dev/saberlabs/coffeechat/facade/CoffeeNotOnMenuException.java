package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.model.CoffeeType;

/**
 * Thrown by {@link CoffeeShopFacade#placeOrder} when the requested {@link CoffeeType} is not on
 * {@code CoffeeShop}'s active menu. Maps to HTTP 409 at the REST layer.
 */
public class CoffeeNotOnMenuException extends RuntimeException {

    public CoffeeNotOnMenuException(CoffeeType type) {
        super(type + " is not on the menu right now");
    }
}
