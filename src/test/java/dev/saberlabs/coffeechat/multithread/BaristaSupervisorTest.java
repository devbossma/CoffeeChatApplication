package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BaristaSupervisor")
class BaristaSupervisorTest {

    private static ThreadPoolTaskExecutor mockExecutor() {
        return mock(ThreadPoolTaskExecutor.class);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null Barista")
        void rejectsNullBarista() {
            assertThrows(NullPointerException.class,
                    () -> new BaristaSupervisor(null, mock(CoffeeShop.class), mockExecutor()));
        }

        @Test
        @DisplayName("rejects a null CoffeeShop")
        void rejectsNullCoffeeShop() {
            assertThrows(NullPointerException.class,
                    () -> new BaristaSupervisor(mock(Barista.class), null, mockExecutor()));
        }

        @Test
        @DisplayName("rejects a null ThreadPoolTaskExecutor")
        void rejectsNullExecutor() {
            assertThrows(NullPointerException.class,
                    () -> new BaristaSupervisor(mock(Barista.class), mock(CoffeeShop.class), null));
        }
    }

    @Nested
    @DisplayName("startBaristas()")
    class StartBaristasTests {

        @Test
        @DisplayName("starts exactly CoffeeShop.baristaPoolSize() consumer loops")
        void startsConfiguredPoolSize() {
            Barista barista = mock(Barista.class);
            CoffeeShop coffeeShop = mock(CoffeeShop.class);
            when(coffeeShop.baristaPoolSize()).thenReturn(4);

            new BaristaSupervisor(barista, coffeeShop, mockExecutor()).startBaristas();

            verify(barista, times(4)).consumeLoop();
        }

        @Test
        @DisplayName("starts a single loop for a pool size of 1")
        void startsSingleLoop() {
            Barista barista = mock(Barista.class);
            CoffeeShop coffeeShop = mock(CoffeeShop.class);
            when(coffeeShop.baristaPoolSize()).thenReturn(1);

            new BaristaSupervisor(barista, coffeeShop, mockExecutor()).startBaristas();

            verify(barista, times(1)).consumeLoop();
        }
    }

    @Nested
    @DisplayName("onContextClosed()")
    class OnContextClosedTests {

        @Test
        @DisplayName("signals the barista to stop and force-interrupts the executor's threads")
        void interruptsExecutorThreads() {
            Barista barista = mock(Barista.class);
            ThreadPoolTaskExecutor executor = mockExecutor();
            ThreadPoolExecutor delegate = mock(ThreadPoolExecutor.class);
            when(executor.getThreadPoolExecutor()).thenReturn(delegate);

            new BaristaSupervisor(barista, mock(CoffeeShop.class), executor).onContextClosed();

            verify(barista).shutdown();
            verify(delegate).shutdownNow();
        }
    }

    @Nested
    @DisplayName("stopBaristas()")
    class StopBaristasTests {

        @Test
        @DisplayName("shuts down the barista")
        void shutsDownBarista() {
            Barista barista = mock(Barista.class);
            CoffeeShop coffeeShop = mock(CoffeeShop.class);

            new BaristaSupervisor(barista, coffeeShop, mockExecutor()).stopBaristas();

            verify(barista).shutdown();
        }
    }
}
