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
}
