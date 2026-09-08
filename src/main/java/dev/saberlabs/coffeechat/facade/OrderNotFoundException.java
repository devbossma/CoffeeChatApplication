package dev.saberlabs.coffeechat.facade;

/**
 * Thrown when a request references an order id that does not exist. Maps to HTTP 404.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("No order with id " + orderId);
    }
}
