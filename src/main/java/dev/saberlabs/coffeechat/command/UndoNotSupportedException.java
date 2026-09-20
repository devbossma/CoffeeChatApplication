package dev.saberlabs.coffeechat.command;

/**
 * Thrown when a command's {@code undo()} cannot be honoured because reversing it would have effects
 * outside the order row (a payment, a loyalty count, a barista's queue entry) that this application
 * does not reverse. Mapped to 409 by the REST layer.
 *
 * <p>Undo is a limited convenience, not a general reversal: only a placement that is still
 * {@code PLACED}, and a cancellation of an order that was {@code READY}, can be undone.
 */
public class UndoNotSupportedException extends RuntimeException {

    public UndoNotSupportedException(String message) {
        super(message);
    }
}
