package dev.saberlabs.coffeechat.command;

import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Pattern 6: COMMAND &mdash; the invoker.
 *
 * <p>Every command runs in <em>exactly one</em> transaction, and is recorded in the history/undo
 * stack only <em>after that transaction has committed</em>. A {@code TransactionTemplate} rather
 * than {@code @Transactional} is what makes that possible: with the annotation, a commit-time
 * failure (an optimistic-lock conflict surfaces at flush/commit) would happen after the command had
 * already been recorded, leaving a history entry for something that never took effect. The barista
 * consumer thread is not inside any transaction, so each facade call it makes gets its own here.
 *
 * <p>The in-memory history/undo stack is a lightweight recent-activity aid, not the audit trail
 * ({@code OrderStatusHistoryEntity} is).
 */
@Service
public class OrderInvoker {

    private static final int MAX_HISTORY = 100;

    private final TransactionTemplate transaction;
    private final Deque<OrderCommand> history = new ArrayDeque<>();
    private final Deque<OrderCommand> undoStack = new ArrayDeque<>();

    public OrderInvoker(@NotNull PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager cannot be null"));
    }

    /**
     * Runs {@code command} in one transaction, then records it. If {@code execute()} throws or the
     * commit fails, the command is <em>not</em> recorded and the exception propagates.
     */
    public void executeCommand(@NotNull OrderCommand command) {
        Objects.requireNonNull(command, "command cannot be null");
        transaction.executeWithoutResult(status -> command.execute());
        synchronized (this) {
            history.addLast(command);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
            undoStack.push(command);
        }
    }

    /**
     * Undoes the most recently executed command, if any, in its own transaction.
     *
     * @return the command that was undone, or {@code null} if there was nothing to undo
     */
    public OrderCommand undoLast() {
        OrderCommand command;
        synchronized (this) {
            command = undoStack.peek();
        }
        if (command == null) {
            return null;
        }
        transaction.executeWithoutResult(status -> command.undo());
        synchronized (this) {
            undoStack.remove(command);
        }
        return command;
    }

    /** Command names in execution order, oldest first. */
    public synchronized List<String> history() {
        List<String> names = new ArrayList<>(history.size());
        history.forEach(c -> names.add(c.name()));
        return names;
    }

    public synchronized int pendingUndoCount() {
        return undoStack.size();
    }
}
