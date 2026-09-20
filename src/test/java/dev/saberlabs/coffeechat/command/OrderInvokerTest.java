package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.support.RecordingTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderInvoker")
class OrderInvokerTest {

    private RecordingTransactionManager transactions;
    private OrderInvoker invoker;

    @BeforeEach
    void setUp() {
        transactions = new RecordingTransactionManager();
        invoker = new OrderInvoker(transactions);
    }

    /** A minimal command that counts its own execute/undo calls. */
    private static class CountingCommand implements OrderCommand {
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger undone = new AtomicInteger();
        private final String name;

        CountingCommand(String name) {
            this.name = name;
        }

        @Override public void execute() { executed.incrementAndGet(); }
        @Override public void undo() { undone.incrementAndGet(); }
        @Override public String name() { return name; }
    }

    @Nested
    @DisplayName("executeCommand()")
    class ExecuteCommandTests {

        @Test
        @DisplayName("runs the command and records it in history")
        void runsAndRecords() {
            CountingCommand cmd = new CountingCommand("A");
            invoker.executeCommand(cmd);
            assertEquals(1, cmd.executed.get());
            assertEquals(List.of("A"), invoker.history());
        }

        @Test
        @DisplayName("history preserves execution order")
        void preservesOrder() {
            invoker.executeCommand(new CountingCommand("A"));
            invoker.executeCommand(new CountingCommand("B"));
            invoker.executeCommand(new CountingCommand("C"));
            assertEquals(List.of("A", "B", "C"), invoker.history());
        }

        @Test
        @DisplayName("a command whose execute() throws is not recorded and the error propagates")
        void notRecordedOnFailure() {
            OrderCommand boom = new OrderCommand() {
                @Override public void execute() { throw new IllegalStateException("boom"); }
                @Override public void undo() { }
                @Override public String name() { return "Boom"; }
            };
            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(boom));
            assertTrue(invoker.history().isEmpty());
            assertEquals(0, invoker.pendingUndoCount());
        }

        @Test
        @DisplayName("runs each command in exactly one committed transaction")
        void oneTransactionPerCommand() {
            invoker.executeCommand(new CountingCommand("A"));
            assertEquals(1, transactions.begun.get());
            assertEquals(1, transactions.committed.get());
            assertEquals(0, transactions.rolledBack.get());
        }

        @Test
        @DisplayName("a command that throws rolls its transaction back")
        void rollsBackOnFailure() {
            OrderCommand boom = new OrderCommand() {
                @Override public void execute() { throw new IllegalStateException("boom"); }
                @Override public void undo() { }
                @Override public String name() { return "Boom"; }
            };
            assertThrows(IllegalStateException.class, () -> invoker.executeCommand(boom));
            assertEquals(1, transactions.rolledBack.get());
            assertEquals(0, transactions.committed.get());
        }

        @Test
        @DisplayName("a command whose transaction fails AT COMMIT (e.g. an optimistic-lock conflict) is not recorded")
        void notRecordedWhenCommitFails() {
            transactions.failCommitsWith(new OptimisticLockingFailureException("stale"));
            CountingCommand cmd = new CountingCommand("A");

            assertThrows(OptimisticLockingFailureException.class, () -> invoker.executeCommand(cmd));

            assertEquals(1, cmd.executed.get(), "execute() itself ran; only the commit failed");
            assertTrue(invoker.history().isEmpty());
            assertEquals(0, invoker.pendingUndoCount());
        }

        @Test
        @DisplayName("rejects a null command")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> invoker.executeCommand(null));
        }

        @Test
        @DisplayName("history is capped at 100 entries, dropping the oldest")
        void historyCapped() {
            for (int i = 0; i < 150; i++) {
                invoker.executeCommand(new CountingCommand("cmd" + i));
            }
            List<String> history = invoker.history();
            assertEquals(100, history.size());
            assertEquals("cmd50", history.get(0));
            assertEquals("cmd149", history.get(history.size() - 1));
        }
    }

    @Nested
    @DisplayName("undoLast()")
    class UndoLastTests {

        @Test
        @DisplayName("undoes the most recently executed command")
        void undoesMostRecent() {
            CountingCommand a = new CountingCommand("A");
            CountingCommand b = new CountingCommand("B");
            invoker.executeCommand(a);
            invoker.executeCommand(b);

            OrderCommand undone = invoker.undoLast(null);

            assertEquals(b, undone);
            assertEquals(1, b.undone.get());
            assertEquals(0, a.undone.get());
        }

        @Test
        @DisplayName("returns null when there is nothing to undo")
        void nullWhenNothing() {
            assertNull(invoker.undoLast(null));
        }

        @Test
        @DisplayName("undo runs in its own committed transaction")
        void undoInTransaction() {
            invoker.executeCommand(new CountingCommand("A"));
            invoker.undoLast(null);
            assertEquals(2, transactions.committed.get());
        }

        @Test
        @DisplayName("an undo that turns out to be unsupported drops that command, so it cannot jam the stack, and earlier commands stay reachable")
        void unsupportedUndoIsDropped() {
            CountingCommand earlier = new CountingCommand("Earlier");
            OrderCommand stale = new OrderCommand() {
                @Override public void execute() { }
                @Override public void undo() { throw new UndoNotSupportedException("the order has moved on"); }
                @Override public String name() { return "Stale"; }
            };
            invoker.executeCommand(earlier);
            invoker.executeCommand(stale);

            assertThrows(UndoNotSupportedException.class, () -> invoker.undoLast(null));

            assertEquals(1, invoker.pendingUndoCount(), "the stale command is gone");
            assertEquals(earlier, invoker.undoLast(null), "the earlier command is reachable again");
        }

        @Test
        @DisplayName("an unexpected failure during undo leaves the command on the stack (nothing was decided)")
        void unexpectedFailureKeepsCommand() {
            OrderCommand flaky = new OrderCommand() {
                @Override public void execute() { }
                @Override public void undo() { throw new IllegalStateException("database down"); }
                @Override public String name() { return "Flaky"; }
            };
            invoker.executeCommand(flaky);

            assertThrows(IllegalStateException.class, () -> invoker.undoLast(null));

            assertEquals(1, invoker.pendingUndoCount());
        }

        @Test
        @DisplayName("executing a command that cannot be undone (payment, fulfilment, preparation) is a barrier: the stack is emptied and undoLast() returns null, it never jams")
        void barrierEmptiesTheStack() {
            invoker.executeCommand(new CountingCommand("Place"));
            invoker.executeCommand(new CountingCommand("Cancel"));
            OrderCommand pay = new CountingCommand("Pay") {
                @Override public boolean undoable() { return false; }
            };

            invoker.executeCommand(pay);

            assertEquals(0, invoker.pendingUndoCount());
            assertNull(invoker.undoLast(null), "defined behaviour: nothing to undo, no exception");
            assertEquals(List.of("Place", "Cancel", "Pay"), invoker.history(), "history still records everything");
        }

        @Test
        @DisplayName("after a barrier, later undoable commands are undoable again (no permanent jam)")
        void undoWorksAgainAfterBarrier() {
            invoker.executeCommand(new CountingCommand("Place") );
            invoker.executeCommand(new CountingCommand("Pay") {
                @Override public boolean undoable() { return false; }
            });
            CountingCommand later = new CountingCommand("PlaceAgain");
            invoker.executeCommand(later);

            assertEquals(later, invoker.undoLast(null));
            assertEquals(1, later.undone.get());
            assertNull(invoker.undoLast(null), "the pre-barrier command is not reachable");
        }

        @Test
        @DisplayName("the undo stack is capped, dropping the oldest, so it cannot leak")
        void undoStackCapped() {
            for (int i = 0; i < OrderInvoker.MAX_HISTORY + 50; i++) {
                invoker.executeCommand(new CountingCommand("cmd" + i));
            }
            assertEquals(OrderInvoker.MAX_HISTORY, invoker.pendingUndoCount());
        }

        @Test
        @DisplayName("undo does not erase the history entry")
        void keepsHistory() {
            invoker.executeCommand(new CountingCommand("A"));
            invoker.undoLast(null);
            assertEquals(List.of("A"), invoker.history());
        }
    }
}
