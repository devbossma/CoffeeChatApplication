package dev.saberlabs.coffeechat.singleton;

import dev.saberlabs.coffeechat.model.CoffeeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CoffeeShop")
class CoffeeShopTest {

    private CoffeeShop shop;

    @BeforeEach
    void setUp() {
        shop = new CoffeeShop(3);
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("starts open with every coffee type on the menu")
        void startsOpenWithFullMenu() {
            assertTrue(shop.isOpen());
            assertEquals(CoffeeType.values().length, shop.activeMenu().size());
        }

        @Test
        @DisplayName("exposes the configured barista pool size")
        void exposesPoolSize() {
            assertEquals(3, shop.baristaPoolSize());
        }

        @Test
        @DisplayName("rejects a pool size below 1")
        void rejectsBadPoolSize() {
            assertThrows(IllegalArgumentException.class, () -> new CoffeeShop(0));
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("stops the shop accepting orders")
        void closes() {
            shop.close();
            assertFalse(shop.isOpen());
        }

        @Test
        @DisplayName("is idempotent")
        void idempotent() {
            shop.close();
            shop.close();
            assertFalse(shop.isOpen());
        }
    }

    @Nested
    @DisplayName("open()")
    class OpenTests {

        @Test
        @DisplayName("re-opens a closed shop")
        void reopens() {
            shop.close();
            shop.open();
            assertTrue(shop.isOpen());
        }
    }

    @Nested
    @DisplayName("isOnMenu()")
    class IsOnMenuTests {

        @Test
        @DisplayName("true for a type currently served")
        void trueForServed() {
            assertTrue(shop.isOnMenu(CoffeeType.LATTE));
        }

        @Test
        @DisplayName("false after that type is pulled from the menu")
        void falseAfterStopServing() {
            shop.stopServing(CoffeeType.LATTE);
            assertFalse(shop.isOnMenu(CoffeeType.LATTE));
        }

        @Test
        @DisplayName("false for null")
        void falseForNull() {
            assertFalse(shop.isOnMenu(null));
        }
    }

    @Nested
    @DisplayName("serve()")
    class ServeTests {

        @Test
        @DisplayName("puts a previously-pulled type back on the menu")
        void putsBack() {
            shop.stopServing(CoffeeType.ESPRESSO);
            shop.serve(CoffeeType.ESPRESSO);
            assertTrue(shop.isOnMenu(CoffeeType.ESPRESSO));
        }
    }

    @Nested
    @DisplayName("activeMenu()")
    class ActiveMenuTests {

        @Test
        @DisplayName("returns an unmodifiable snapshot")
        void unmodifiableSnapshot() {
            assertThrows(UnsupportedOperationException.class,
                    () -> shop.activeMenu().add(CoffeeType.LATTE));
        }

        @Test
        @DisplayName("a later menu change does not affect an earlier snapshot")
        void snapshotIsDetached() {
            var snapshot = shop.activeMenu();
            shop.stopServing(CoffeeType.LATTE);
            assertTrue(snapshot.contains(CoffeeType.LATTE));
        }
    }

    @Nested
    @DisplayName("reset()")
    class ResetTests {

        @Test
        @DisplayName("restores the open, full-menu default state")
        void restoresDefaults() {
            shop.close();
            shop.stopServing(CoffeeType.LATTE);
            shop.reset();
            assertTrue(shop.isOpen());
            assertTrue(shop.isOnMenu(CoffeeType.LATTE));
        }
    }
}
