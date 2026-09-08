package dev.saberlabs.coffeechat.factory;

import dev.saberlabs.coffeechat.model.Cappuccino;
import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.Latte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("CoffeeFactory")
class CoffeeFactoryTest {

    private CoffeeFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CoffeeFactory();
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("ESPRESSO -> a $2.50 Espresso")
        void createsEspresso() {
            Coffee coffee = factory.create(CoffeeType.ESPRESSO);
            assertInstanceOf(Espresso.class, coffee);
            assertEquals(CoffeeType.ESPRESSO, coffee.type());
            assertEquals(new BigDecimal("2.50"), coffee.cost());
        }

        @Test
        @DisplayName("CAPPUCCINO -> a $3.50 Cappuccino")
        void createsCappuccino() {
            Coffee coffee = factory.create(CoffeeType.CAPPUCCINO);
            assertInstanceOf(Cappuccino.class, coffee);
            assertEquals(new BigDecimal("3.50"), coffee.cost());
        }

        @Test
        @DisplayName("LATTE -> a $4.00 Latte")
        void createsLatte() {
            Coffee coffee = factory.create(CoffeeType.LATTE);
            assertInstanceOf(Latte.class, coffee);
            assertEquals(new BigDecimal("4.00"), coffee.cost());
        }

        @Test
        @DisplayName("returns a fresh instance on every call")
        void freshInstanceEachCall() {
            assertNotSame(factory.create(CoffeeType.LATTE), factory.create(CoffeeType.LATTE));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for a null type")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> factory.create(null));
        }
    }
}
