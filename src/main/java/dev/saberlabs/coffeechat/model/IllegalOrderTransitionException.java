package dev.saberlabs.coffeechat.model;

/**
 * An order was asked to make a status move its current status does not allow. A subtype of
 * {@link IllegalStateException} so existing callers keep working, but specific enough for the
 * barista to skip exactly this (a normal outcome when another writer got there first) without also
 * swallowing an unrelated {@code IllegalStateException} (such as the publisher's no-transaction
 * error) as a quiet INFO line.
 */
public class IllegalOrderTransitionException extends IllegalStateException {

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Illegal order transition: " + from + " -> " + to);
    }
}
