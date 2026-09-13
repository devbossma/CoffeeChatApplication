package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BaristaSupervisor")
class BaristaSupervisorTest {

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null Barista")
        void rejectsNullBarista() {
            assertThrows(NullPointerException.class,
                    () -> new BaristaSupervisor(null, mock(CoffeeShop.class)));
        }

        @Test
        @DisplayName("rejects a null CoffeeShop")
        void rejectsNullCoffeeShop() {
            assertThrows(NullPointerException.class,
                    () -> new BaristaSupervisor(mock(Barista.class), null));
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

            new BaristaSupervisor(barista, coffeeShop).startBaristas();

            verify(barista, times(4)).consumeLoop();
        }

        @Test
        @DisplayName("starts a single loop for a pool size of 1")
        void startsSingleLoop() {
            Barista barista = mock(Barista.class);
            CoffeeShop coffeeShop = mock(CoffeeShop.class);
            when(coffeeShop.baristaPoolSize()).thenReturn(1);

            new BaristaSupervisor(barista, coffeeShop).startBaristas();

            verify(barista, times(1)).consumeLoop();
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

            new BaristaSupervisor(barista, coffeeShop).stopBaristas();

            verify(barista).shutdown();
        }
    }
}
