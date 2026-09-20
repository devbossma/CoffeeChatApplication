package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.OrderCommandParser.MissingCoffeeType;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.NotAnOrder;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.Parsed;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.UnknownCoffeeType;
import dev.saberlabs.coffeechat.chat.OrderCommandParser.UnknownExtras;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderCommandParser")
class OrderCommandParserTest {

    private OrderCommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new OrderCommandParser();
    }

    private Parsed parsed(String input) {
        return assertInstanceOf(Parsed.class, parser.parse(input), input);
    }

    @Nested
    @DisplayName("the /order marker")
    class MarkerTests {

        @Test
        @DisplayName("plain small talk is not an order")
        void smallTalk() {
            assertInstanceOf(NotAnOrder.class, parser.parse("hello, how are you?"));
        }

        @Test
        @DisplayName("the PRD's false-positive sentence (no slash) never triggers an order")
        void prdFalsePositive() {
            assertInstanceOf(NotAnOrder.class, parser.parse("order latte from this place was amazing"));
        }

        @Test
        @DisplayName("a sentence that merely CONTAINS /order later on is not an order")
        void markerMustBeFirst() {
            assertInstanceOf(NotAnOrder.class, parser.parse("please /order latte"));
        }

        @Test
        @DisplayName("look-alike first tokens (/orders, /ordering, /order-x, order) are not the marker")
        void lookAlikes() {
            for (String input : List.of("/orders latte", "/ordering latte", "/order-x latte", "order latte", "//order latte")) {
                assertInstanceOf(NotAnOrder.class, parser.parse(input), input);
            }
        }

        @Test
        @DisplayName("blank and empty input are not orders")
        void blank() {
            assertInstanceOf(NotAnOrder.class, parser.parse(""));
            assertInstanceOf(NotAnOrder.class, parser.parse("    \t\n "));
        }

        @Test
        @DisplayName("the marker is case-insensitive")
        void markerCase() {
            assertEquals(CoffeeType.LATTE, parsed("/ORDER latte").coffee());
            assertEquals(CoffeeType.LATTE, parsed("/Order latte").coffee());
        }

        @Test
        @DisplayName("rejects null input")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> parser.parse(null));
        }
    }

    @Nested
    @DisplayName("whitespace handling")
    class WhitespaceTests {

        @Test
        @DisplayName("leading and trailing whitespace is ignored")
        void trimmed() {
            assertEquals(CoffeeType.ESPRESSO, parsed("   /order espresso   ").coffee());
        }

        @Test
        @DisplayName("a leading or trailing non-breaking / Unicode space does not hide the marker")
        void leadingUnicodeSpace() {
            for (String space : List.of("\u00A0", "\u2007", "\u202F", "\u2003", "\u3000")) {
                Parsed result = parsed(space + "/order" + space + "latte" + space);
                assertEquals(CoffeeType.LATTE, result.coffee());
            }
        }

        @Test
        @DisplayName("any run of spaces, tabs, newlines or non-breaking spaces separates tokens")
        void anyWhitespace() {
            Parsed result = parsed("/order\t\tlatte \n  milk sugar");
            assertEquals(CoffeeType.LATTE, result.coffee());
            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR), result.extras());
        }
    }

    @Nested
    @DisplayName("the coffee")
    class CoffeeTests {

        @Test
        @DisplayName("every coffee type is recognised, in any case")
        void allCoffees() {
            assertEquals(CoffeeType.ESPRESSO, parsed("/order espresso").coffee());
            assertEquals(CoffeeType.CAPPUCCINO, parsed("/order Cappuccino").coffee());
            assertEquals(CoffeeType.LATTE, parsed("/order LATTE").coffee());
        }

        @Test
        @DisplayName("a bare /order reports the missing coffee")
        void missing() {
            assertInstanceOf(MissingCoffeeType.class, parser.parse("/order"));
            assertInstanceOf(MissingCoffeeType.class, parser.parse("  /order   "));
        }

        @Test
        @DisplayName("an unknown coffee is reported, lower-cased")
        void unknown() {
            UnknownCoffeeType result = assertInstanceOf(UnknownCoffeeType.class, parser.parse("/order Mocha milk"));
            assertEquals("mocha", result.typed());
        }
    }

    @Nested
    @DisplayName("the extras")
    class ExtrasTests {

        @Test
        @DisplayName("no extras is an empty list")
        void none() {
            assertEquals(List.of(), parsed("/order espresso").extras());
        }

        @Test
        @DisplayName("milk, sugar and whipped cream are recognised, including the aliases")
        void allExtras() {
            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR, ExtraType.WHIPPED_CREAM),
                    parsed("/order latte milk sugar whipped").extras());
            assertEquals(List.of(ExtraType.WHIPPED_CREAM), parsed("/order latte whippedcream").extras());
            assertEquals(List.of(ExtraType.WHIPPED_CREAM), parsed("/order latte WHIPPED_CREAM").extras());
        }

        @Test
        @DisplayName("duplicate extras are allowed and keep their typed order (two milks are two milks)")
        void duplicatesKept() {
            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR, ExtraType.MILK),
                    parsed("/order latte milk sugar milk").extras());
        }

        @Test
        @DisplayName("ALL unknown extras are reported, lower-cased, in typed order, not just the first")
        void allUnknownReported() {
            UnknownExtras result = assertInstanceOf(UnknownExtras.class, parser.parse("/order latte milk Caramel sugar Vanilla"));
            assertEquals(List.of("caramel", "vanilla"), result.typed());
        }

        @Test
        @DisplayName("one unknown extra makes the whole command unknown: nothing is partially accepted")
        void noPartialAcceptance() {
            assertInstanceOf(UnknownExtras.class, parser.parse("/order latte milk nonsense"));
        }
    }

    @Nested
    @DisplayName("Parsed / UnknownExtras records")
    class RecordTests {

        @Test
        @DisplayName("Parsed defensively copies its extras and rejects nulls")
        void parsedContract() {
            List<ExtraType> mutable = new java.util.ArrayList<>(List.of(ExtraType.MILK));
            Parsed result = new Parsed(CoffeeType.LATTE, mutable);
            mutable.add(ExtraType.SUGAR);
            assertEquals(List.of(ExtraType.MILK), result.extras());
            assertThrows(NullPointerException.class, () -> new Parsed(null, List.of()));
            assertThrows(NullPointerException.class, () -> new Parsed(CoffeeType.LATTE, null));
        }

        @Test
        @DisplayName("UnknownExtras is immutable and rejects null")
        void unknownExtrasContract() {
            assertThrows(UnsupportedOperationException.class, () -> new UnknownExtras(List.of("x")).typed().add("y"));
            assertThrows(NullPointerException.class, () -> new UnknownExtras(null));
        }
    }

    @Test
    @DisplayName("lists the coffees and extras a customer may name, for use in replies")
    void availableLists() {
        assertEquals(List.of("espresso", "cappuccino", "latte"), parser.availableCoffees());
        assertEquals(List.of("milk", "sugar", "whipped"), parser.availableExtras());
    }
}
