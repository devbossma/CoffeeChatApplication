package dev.saberlabs.coffeechat.command;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pattern 6: COMMAND &mdash; the Invoker.
 *
 * <p>The only class that calls {@code execute()} / {@code undo()}. It keeps a recent-activity
 * history and an undo stack. Per {@code CLAUDE.md} this history is a lightweight in-memory aid,
 * <em>not</em> the durable audit trail (that is the Part 03 {@code OrderStatusHistoryEntity},
 * written from the one event listener) &mdash; so it is capped rather than grown without bound.
 *
 * <p>{@code @Transactional} is added in Part 03, once {@code OrderService} is JPA-backed and a
 * command actually touches the database (PRD &sect;7.2). In Part 01 there is nothing
 * transactional to wrap.
 */
@Service
public class OrderInvoker {

    private static final int MAX_HISTORY = 100;

    private final Deque<OrderCommand> history = new ArrayDeque<>();
    private final Deque<OrderCommand> undoStack = new ArrayDeque<>();

    /**
     * Runs {@code command}, then records it. If {@code execute()} throws, the command is
     * <em>not</em> recorded and the exception propagates.
     */
    public void executeCommand(OrderCommand command) {
        command.execute();
        history.addLast(command);
        if (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }
        undoStack.push(command);
    }

    /**
     * Undoes the most recently executed command, if any.
     *
     * @return the command that was undone, or {@code null} if there was nothing to undo
     */
    public OrderCommand undoLast() {
        if (undoStack.isEmpty()) {
            return null;
        }
        OrderCommand command = undoStack.pop();
        command.undo();
        return command;
    }

    /** Command names in execution order, oldest first. */
    public List<String> history() {
        List<String> names = new ArrayList<>(history.size());
        history.forEach(c -> names.add(c.name()));
        return names;
    }

    public int pendingUndoCount() {
        return undoStack.size();
    }
}
