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
 *
 * <p><b>Undo is a limited convenience.</b> Only undoable commands ({@link OrderCommand#undoable()})
 * are pushed; executing one that is not (payment, fulfilment, preparation) is a <em>barrier</em> that
 * empties the stack, because anything before it can no longer be safely reversed. That keeps the top of
 * the stack always something that can actually be undone (it cannot jam behind an un-undoable command)
 * and, together with the cap, keeps the stack bounded. A command whose undo turns out to be
 * unsupported at that moment (the order has since moved on) is dropped from the stack and the
 * exception propagates.
 */
@Service
public class OrderInvoker {

    static final int MAX_HISTORY = 100;

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
            if (command.undoable()) {
                undoStack.push(command);
                if (undoStack.size() > MAX_HISTORY) {
                    undoStack.removeLast();
                }
            } else {
                undoStack.clear();
            }
        }
    }

    /**
     * Undoes the most recently executed undoable command, if any, in its own transaction.
     *
     * @return the command that was undone, or {@code null} if there was nothing to undo (including
     *         when the last thing done was a barrier command such as a payment)
     * @throws UndoNotSupportedException if the order has moved on since (the command is then dropped)
     */
    public OrderCommand undoLast() {
        OrderCommand command;
        synchronized (this) {
            command = undoStack.peek();
        }
        if (command == null) {
            return null;
        }
        try {
            transaction.executeWithoutResult(status -> command.undo());
        } catch (UndoNotSupportedException e) {
            synchronized (this) {
                undoStack.remove(command);
            }
            throw e;
        }
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
