package dev.saberlabs.coffeechat.facade;

/**
 * Thrown by {@link CoffeeShopFacade#placeOrder} when {@code CoffeeShop} is closed. Maps to
 * HTTP 409 at the REST layer.
 */
public class ShopClosedException extends RuntimeException {

    public ShopClosedException() {
        super("The shop is closed and is not accepting new orders");
    }
}
