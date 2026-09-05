package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.ExtraType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CoffeeDecorators")
class CoffeeDecoratorsTest {

    @Nested
    @DisplayName("decorate()")
    class DecorateTests {

        @Test
        @DisplayName("returns the base coffee unchanged when there are no extras")
        void noExtrasReturnsBase() {
            Coffee base = new Espresso();
            assertSame(base, CoffeeDecorators.decorate(base, List.of()));
        }

        @Test
        @DisplayName("wraps a single extra")
        void singleExtra() {
            Coffee result = CoffeeDecorators.decorate(new Espresso(), List.of(ExtraType.MILK));
            assertEquals("Espresso + Milk", result.description());
            assertEquals(new BigDecimal("3.00"), result.cost());
        }

        @Test
        @DisplayName("applies multiple extras in list order")
        void multipleInOrder() {
            Coffee result = CoffeeDecorators.decorate(
                    new Espresso(), List.of(ExtraType.SUGAR, ExtraType.MILK, ExtraType.WHIPPED_CREAM));
            assertEquals("Espresso + Sugar + Milk + Whipped Cream", result.description());
            assertEquals(new BigDecimal("4.00"), result.cost());
        }

        @Test
        @DisplayName("rejects a null base coffee")
        void rejectsNullBase() {
            assertThrows(NullPointerException.class,
                    () -> CoffeeDecorators.decorate(null, List.of(ExtraType.MILK)));
        }

        @Test
        @DisplayName("rejects a null extras list")
        void rejectsNullList() {
            assertThrows(NullPointerException.class,
                    () -> CoffeeDecorators.decorate(new Espresso(), null));
        }

        @Test
        @DisplayName("rejects a null element in the extras list")
        void rejectsNullElement() {
            assertThrows(NullPointerException.class,
                    () -> CoffeeDecorators.decorate(new Espresso(), Arrays.asList(ExtraType.MILK, null)));
        }
    }
}
