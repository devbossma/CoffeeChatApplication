package dev.saberlabs.coffeechat.command;

/**
 * Pattern 6: COMMAND.
 *
 * <p>One order-lifecycle action as an object that knows how to {@link #execute()} itself and how
 * to {@link #undo()} itself. {@code OrderInvoker} runs them, keeps a history, and can reverse the
 * most recent one. Same shape as {@code MyDesignPattern}; only {@code OrderInvoker} becomes a
 * Spring {@code @Service}, and {@code CoffeeShopFacade} is the sole builder of these objects
 * ({@code CLAUDE.md}).
 */
public interface OrderCommand {

    /** Perform the action. May throw if the action fails (e.g. a declined payment). */
    void execute();

    /** Reverse the action's effect as far as is meaningful. */
    void undo();

    /** Short stable name for the history log, e.g. {@code "PlaceOrder"}. */
    String name();

    /**
     * Whether this kind of command can ever be undone. A command that cannot (payment, fulfilment,
     * preparation: their reversal has effects outside the order row) is a <em>barrier</em> for
     * {@code OrderInvoker}: nothing executed before it can be safely undone any more, so executing it
     * empties the undo stack instead of leaving a command on top that can never be undone.
     */
    default boolean undoable() {
        return true;
    }

    /**
     * The user this command acts on behalf of, recorded as {@code order_status_history.changed_by};
     * {@code null} means an automated/system action.
     *
     * <p><b>Step 4 seam.</b> Step 3 never overrides this, so every history row has a NULL actor.
     * Step 4 adds the real actor path here, together with the application-layer check that the
     * actor's role is BARISTA (a plain foreign key cannot enforce that).
     */
    default Long actorUserId() {
        return null;
    }

    /**
     * Attributes the status changes an {@link #undo()} is about to make to {@code userId} (the user
     * who asked for the undo, {@code null} for system), instead of to whoever originally ran the command.
     */
    default void attributeUndoTo(Long userId) {
    }
}
