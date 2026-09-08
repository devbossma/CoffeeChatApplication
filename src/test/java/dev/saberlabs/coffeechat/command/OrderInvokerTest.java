package dev.saberlabs.coffeechat.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderInvoker")
class OrderInvokerTest {

    private OrderInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new OrderInvoker();
    }

    /** A minimal command that counts its own execute/undo calls. */
    private static final class CountingCommand implements OrderCommand {
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

            OrderCommand undone = invoker.undoLast();

            assertEquals(b, undone);
            assertEquals(1, b.undone.get());
            assertEquals(0, a.undone.get());
        }

        @Test
        @DisplayName("returns null when there is nothing to undo")
        void nullWhenNothing() {
            assertNull(invoker.undoLast());
        }

        @Test
        @DisplayName("undo does not erase the history entry")
        void keepsHistory() {
            invoker.executeCommand(new CountingCommand("A"));
            invoker.undoLast();
            assertEquals(List.of("A"), invoker.history());
        }
    }
}
