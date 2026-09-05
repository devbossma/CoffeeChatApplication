package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three concrete Factory Method products. Their prices are the values carried over from
 * {@code MyDesignPattern} (PRD &sect;6); {@code CoffeeFactory} is what will hand them out in the
 * Factory Method commit.
 */
@DisplayName("Coffee (concrete products)")
class CoffeeTest {

    @Nested
    @DisplayName("Espresso")
    class EspressoTests {

        @Test
        @DisplayName("is a $2.50 ESPRESSO described as \"Espresso\"")
        void espresso() {
            Coffee coffee = new Espresso();
            assertEquals("Espresso", coffee.description());
            assertEquals(new BigDecimal("2.50"), coffee.cost());
            assertEquals(CoffeeType.ESPRESSO, coffee.type());
        }
    }

    @Nested
    @DisplayName("Cappuccino")
    class CappuccinoTests {

        @Test
        @DisplayName("is a $3.50 CAPPUCCINO described as \"Cappuccino\"")
        void cappuccino() {
            Coffee coffee = new Cappuccino();
            assertEquals("Cappuccino", coffee.description());
            assertEquals(new BigDecimal("3.50"), coffee.cost());
            assertEquals(CoffeeType.CAPPUCCINO, coffee.type());
        }
    }

    @Nested
    @DisplayName("Latte")
    class LatteTests {

        @Test
        @DisplayName("is a $4.00 LATTE described as \"Latte\"")
        void latte() {
            Coffee coffee = new Latte();
            assertEquals("Latte", coffee.description());
            assertEquals(new BigDecimal("4.00"), coffee.cost());
            assertEquals(CoffeeType.LATTE, coffee.type());
        }
    }

    @Nested
    @DisplayName("CoffeeType.displayName()")
    class DisplayNameTests {

        @Test
        @DisplayName("each type carries its human-readable label")
        void displayNames() {
            assertEquals("Espresso", CoffeeType.ESPRESSO.displayName());
            assertEquals("Cappuccino", CoffeeType.CAPPUCCINO.displayName());
            assertEquals("Latte", CoffeeType.LATTE.displayName());
        }
    }
}
