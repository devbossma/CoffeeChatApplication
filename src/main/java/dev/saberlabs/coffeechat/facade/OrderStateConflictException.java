package dev.saberlabs.coffeechat.facade;

/**
 * The order is in a state that does not allow the requested action: paying an order that is not
 * READY, paying one that is already PAID, or fulfilling one whose payment is not PAID. Mapped to 409.
 */
public class OrderStateConflictException extends RuntimeException {

    public OrderStateConflictException(String message) {
        super(message);
    }
}
