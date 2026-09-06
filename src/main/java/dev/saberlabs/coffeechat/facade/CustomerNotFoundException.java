package dev.saberlabs.coffeechat.facade;

/**
 * Thrown when a request references a customer id that does not exist. Maps to HTTP 404.
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("No customer with id " + customerId);
    }
}
