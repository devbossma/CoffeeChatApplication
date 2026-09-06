package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.*;
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
    @DisplayName("---- DECORATION TESTING ----")
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

    @Nested
    @DisplayName("--- COST CALCULATION TESTING ---")
    class CostTests {

        @Test
        @DisplayName("milk adds $0.50 to the wrapped coffee")
        void milkAdds() {
            assertEquals(new BigDecimal("3.00"), new MilkDecorator(new Espresso()).cost());
        }

        @Test
        @DisplayName("sugar adds $0.25 to the wrapped coffee")
        void sugarAdds() {
            assertEquals(new BigDecimal("2.75"), new SugarDecorator(new Espresso()).cost());
        }

        @Test
        @DisplayName("whipped cream adds $0.75 to the wrapped coffee")
        void whippedAdds() {
            assertEquals(new BigDecimal("3.25"), new WhippedCreamDecorator(new Espresso()).cost());
        }

        @Test
        @DisplayName("stacked decorators accumulate their surcharges")
        void stackAccumulates() {
            Coffee coffee = new WhippedCreamDecorator(
                    new MilkDecorator(
                            new SugarDecorator(new Espresso())));
            // 2.50 + 0.25 + 0.50 + 0.75
            assertEquals(new BigDecimal("4.00"), coffee.cost());
        }

        @Test
        @DisplayName("total is independent of the order extras are applied in")
        void orderIndependent() {
            Coffee a = new MilkDecorator(new SugarDecorator(new Latte()));
            Coffee b = new SugarDecorator(new MilkDecorator(new Latte()));
            assertEquals(a.cost(), b.cost());
        }
    }

    @Nested
    @DisplayName("---- DESCRIPTION TESTING ----")
    class DescriptionTests {

        @Test
        @DisplayName("appends the extra's label to the wrapped description")
        void appendsLabel() {
            assertEquals("Espresso + Milk", new MilkDecorator(new Espresso()).description());
        }

        @Test
        @DisplayName("stacked decorators build the description outward")
        void stacked() {
            Coffee coffee = new MilkDecorator(new SugarDecorator(new Espresso()));
            assertEquals("Espresso + Sugar + Milk", coffee.description());
        }
    }

    @Nested
    @DisplayName("---- TYPE VALIDATION TESTING ----")
    class TypeTests {

        @Test
        @DisplayName("wrapping does not change the base coffee type")
        void keepsBaseType() {
            Coffee coffee = new WhippedCreamDecorator(new MilkDecorator(new Latte()));
            assertEquals(CoffeeType.LATTE, coffee.type());
        }
    }

    @Nested
    @DisplayName("---- CONSTRUCTOR VALIDATION TESTING ----")
    class ConstructorTests {

        @Test
        @DisplayName("rejects a null wrapped coffee")
        void rejectsNullInner() {
            assertThrows(NullPointerException.class, () -> new MilkDecorator(null));
        }
    }
}
