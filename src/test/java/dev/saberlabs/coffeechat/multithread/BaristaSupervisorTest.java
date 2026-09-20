package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BaristaSupervisor")
class BaristaSupervisorTest {

    private Barista barista;
    private CoffeeShop coffeeShop;

    @BeforeEach
    void setUp() {
        barista = mock(Barista.class);
        coffeeShop = mock(CoffeeShop.class);
        when(coffeeShop.baristaPoolSize()).thenReturn(3);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null Barista")
        void rejectsNullBarista() {
            assertThrows(NullPointerException.class, () -> new BaristaSupervisor(null, coffeeShop));
        }

        @Test
        @DisplayName("rejects a null CoffeeShop")
        void rejectsNullCoffeeShop() {
            assertThrows(NullPointerException.class, () -> new BaristaSupervisor(barista, null));
        }
    }

    @Nested
    @DisplayName("start()")
    class StartTests {

        @Test
        @DisplayName("starts exactly CoffeeShop.baristaPoolSize() consumer loops")
        void startsConfiguredPoolSize() {
            when(coffeeShop.baristaPoolSize()).thenReturn(4);

            new BaristaSupervisor(barista, coffeeShop).start();

            verify(barista, times(4)).consumeLoop();
        }

        @Test
        @DisplayName("starts a single loop for a pool size of 1")
        void startsSingleLoop() {
            when(coffeeShop.baristaPoolSize()).thenReturn(1);

            new BaristaSupervisor(barista, coffeeShop).start();

            verify(barista, times(1)).consumeLoop();
        }

        @Test
        @DisplayName("a second start while already running does not launch a second set of loops")
        void secondStartIsNoOp() {
            BaristaSupervisor supervisor = new BaristaSupervisor(barista, coffeeShop);

            supervisor.start();
            supervisor.start();

            verify(barista, times(3)).consumeLoop();
        }
    }

    @Nested
    @DisplayName("stop() / stopBaristas()")
    class StopTests {

        @Test
        @DisplayName("signals the barista to stop and reports not running")
        void stopSignalsBarista() {
            BaristaSupervisor supervisor = new BaristaSupervisor(barista, coffeeShop);
            supervisor.start();
            assertTrue(supervisor.isRunning());

            supervisor.stop();

            verify(barista).shutdown();
            assertFalse(supervisor.isRunning());
        }

        @Test
        @DisplayName("stopBaristas() (the @PreDestroy fallback) also shuts the barista down")
        void stopBaristasShutsDown() {
            new BaristaSupervisor(barista, coffeeShop).stopBaristas();

            verify(barista).shutdown();
        }

        @Test
        @DisplayName("start() after stop() (a resumed, previously paused context) re-arms the barista and relaunches every loop")
        void restartsAfterStop() {
            BaristaSupervisor supervisor = new BaristaSupervisor(barista, coffeeShop);
            supervisor.start();
            supervisor.stop();

            supervisor.start();

            assertTrue(supervisor.isRunning());
            verify(barista, times(6)).consumeLoop();
            var order = inOrder(barista);
            order.verify(barista).restart();
            order.verify(barista, times(3)).consumeLoop();
            order.verify(barista).shutdown();
            order.verify(barista).restart();
        }

        @Test
        @DisplayName("never launches loops merely because stop() was called")
        void stopDoesNotStart() {
            new BaristaSupervisor(barista, coffeeShop).stop();

            verify(barista, never()).consumeLoop();
        }
    }

    @Nested
    @DisplayName("SmartLifecycle contract")
    class LifecycleContractTests {

        private final BaristaSupervisor supervisor = new BaristaSupervisor(mock(Barista.class), mock(CoffeeShop.class));

        @Test
        @DisplayName("stops before the executor: its phase is above ThreadPoolTaskExecutor's (lifecycle beans stop in descending phase order)")
        void phaseAboveExecutor() {
            assertEquals(Integer.MAX_VALUE / 2 + 1, supervisor.getPhase());
            assertTrue(supervisor.getPhase() > Integer.MAX_VALUE / 2);
        }

        @Test
        @DisplayName("is auto-started, so it is also restarted when a paused context is resumed")
        void autoStartup() {
            assertTrue(supervisor.isAutoStartup());
            assertTrue(supervisor instanceof SmartLifecycle);
        }
    }
}
