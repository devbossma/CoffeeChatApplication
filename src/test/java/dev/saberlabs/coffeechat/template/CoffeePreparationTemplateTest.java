package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CoffeePreparationTemplate")
class CoffeePreparationTemplateTest {

    @Nested
    @DisplayName("prepare()")
    class PrepareTests {

        @Test
        @DisplayName("runs the fixed skeleton in order: boil -> brew -> pour -> condiments -> serve")
        void fixedOrder() {
            CoffeePreparationTemplate prep = new EspressoPreparation();
            prep.prepare();
            List<String> log = prep.log();

            assertTrue(log.get(0).startsWith("Boiling water"), "boil water is first");
            int pour = log.indexOf("Pouring into cup");
            int brew = log.indexOf("Pulling a single espresso shot");
            int condiments = log.indexOf("No condiments for espresso");
            assertTrue(brew > 0 && brew < pour, "brew comes after boil, before pour");
            assertTrue(pour < condiments, "pour comes before condiments");
            assertEquals("Espresso is ready", log.get(log.size() - 1), "serve is last");
        }

        @Test
        @DisplayName("boil step records this coffee type's temperature and duration")
        void boilRecordsTemperatureAndDuration() {
            CoffeePreparationTemplate prep = new LattePreparation();
            prep.prepare();
            assertTrue(prep.log().get(0).contains("93°C"));
            assertTrue(prep.log().get(0).contains("28 seconds"));
        }

        @Test
        @DisplayName("a milk drink's brew has more steps than an espresso's")
        void milkDrinkHasMoreBrewSteps() {
            CoffeePreparationTemplate espresso = new EspressoPreparation();
            CoffeePreparationTemplate cappuccino = new CappuccinoPreparation();
            espresso.prepare();
            cappuccino.prepare();
            assertTrue(cappuccino.log().size() > espresso.log().size());
        }

        @Test
        @DisplayName("the latte recipe adds vanilla and cocoa as condiments")
        void latteCondiments() {
            CoffeePreparationTemplate prep = new LattePreparation();
            prep.prepare();
            assertTrue(prep.log().contains("Finishing with vanilla and cocoa"));
        }
    }

    @Nested
    @DisplayName("log()")
    class LogTests {

        @Test
        @DisplayName("is empty before prepare() has run")
        void emptyBeforePrepare() {
            assertTrue(new EspressoPreparation().log().isEmpty());
        }

        @Test
        @DisplayName("is unmodifiable")
        void unmodifiable() {
            CoffeePreparationTemplate prep = new EspressoPreparation();
            prep.prepare();
            assertThrows(UnsupportedOperationException.class, () -> prep.log().add("tamper"));
        }
    }

    @Nested
    @DisplayName("coffeeType()")
    class CoffeeTypeTests {

        @Test
        @DisplayName("reports the type the concrete preparation is for")
        void reportsType() {
            assertEquals(CoffeeType.CAPPUCCINO, new CappuccinoPreparation().coffeeType());
        }
    }
}
