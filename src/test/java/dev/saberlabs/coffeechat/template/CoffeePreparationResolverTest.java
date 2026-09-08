package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CoffeePreparationResolver")
class CoffeePreparationResolverTest {

    private CoffeePreparationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CoffeePreparationResolver();
    }

    @Nested
    @DisplayName("forType()")
    class ForTypeTests {

        @Test
        @DisplayName("maps each coffee type to its matching preparation")
        void mapsEachType() {
            assertInstanceOf(EspressoPreparation.class, resolver.forType(CoffeeType.ESPRESSO));
            assertInstanceOf(CappuccinoPreparation.class, resolver.forType(CoffeeType.CAPPUCCINO));
            assertInstanceOf(LattePreparation.class, resolver.forType(CoffeeType.LATTE));
        }

        @Test
        @DisplayName("returns a fresh, un-run template on every call")
        void freshInstanceEachCall() {
            CoffeePreparationTemplate first = resolver.forType(CoffeeType.LATTE);
            first.prepare();
            CoffeePreparationTemplate second = resolver.forType(CoffeeType.LATTE);
            assertNotSame(first, second);
            assertTrue(second.log().isEmpty());
        }

        @Test
        @DisplayName("the resolved template is for the requested type")
        void resolvedTypeMatches() {
            assertEquals(CoffeeType.CAPPUCCINO, resolver.forType(CoffeeType.CAPPUCCINO).coffeeType());
        }

        @Test
        @DisplayName("throws for a null type")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> resolver.forType(null));
        }
    }
}
