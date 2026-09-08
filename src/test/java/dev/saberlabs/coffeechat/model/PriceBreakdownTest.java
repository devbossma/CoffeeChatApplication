package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PriceBreakdown")
class PriceBreakdownTest {

    @Nested
    @DisplayName("of()")
    class OfTests {

        @Test
        @DisplayName("computes total as base + extras - discount")
        void computesTotal() {
            PriceBreakdown p = PriceBreakdown.of(
                    new BigDecimal("4.00"), new BigDecimal("0.75"), new BigDecimal("0.95"));
            assertEquals(new BigDecimal("3.80"), p.total());
        }

        @Test
        @DisplayName("normalises every component to a scale of 2")
        void normalisesScale() {
            PriceBreakdown p = PriceBreakdown.of(
                    new BigDecimal("2.5"), new BigDecimal("0"), new BigDecimal("0"));
            assertEquals(new BigDecimal("2.50"), p.base());
            assertEquals(new BigDecimal("0.00"), p.extras());
            assertEquals(new BigDecimal("2.50"), p.total());
        }

        @Test
        @DisplayName("rejects a negative component")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> PriceBreakdown.of(
                    new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("-1.00")));
        }

        @Test
        @DisplayName("rejects a null component")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> PriceBreakdown.of(
                    null, new BigDecimal("0.00"), new BigDecimal("0.00")));
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts a self-consistent breakdown")
        void acceptsConsistent() {
            PriceBreakdown p = new PriceBreakdown(
                    new BigDecimal("3.50"), new BigDecimal("0.50"),
                    new BigDecimal("0.40"), new BigDecimal("3.60"));
            assertEquals(new BigDecimal("3.60"), p.total());
        }

        @Test
        @DisplayName("rejects a total that does not equal base + extras - discount")
        void rejectsInconsistentTotal() {
            assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown(
                    new BigDecimal("3.50"), new BigDecimal("0.50"),
                    new BigDecimal("0.40"), new BigDecimal("9.99")));
        }

        @Test
        @DisplayName("rejects a negative component")
        void rejectsNegativeComponent() {
            assertThrows(IllegalArgumentException.class, () -> new PriceBreakdown(
                    new BigDecimal("-3.50"), new BigDecimal("0.50"),
                    new BigDecimal("0.00"), new BigDecimal("-3.00")));
        }
    }
}
