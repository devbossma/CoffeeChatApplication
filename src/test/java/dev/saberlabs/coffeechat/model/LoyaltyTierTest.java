package dev.saberlabs.coffeechat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("LoyaltyTier")
class LoyaltyTierTest {

    @Nested
    @DisplayName("forCount()")
    class ForCountTests {

        @Test
        @DisplayName("0 fulfilled orders is REGULAR (lower bound)")
        void zeroIsRegular() {
            assertEquals(LoyaltyTier.REGULAR, LoyaltyTier.forCount(0));
        }

        @Test
        @DisplayName("5 fulfilled orders is still REGULAR (upper bound of the band)")
        void fiveIsRegular() {
            assertEquals(LoyaltyTier.REGULAR, LoyaltyTier.forCount(5));
        }

        @Test
        @DisplayName("6 fulfilled orders crosses into SILVER (lower bound)")
        void sixIsSilver() {
            assertEquals(LoyaltyTier.SILVER, LoyaltyTier.forCount(6));
        }

        @Test
        @DisplayName("10 fulfilled orders is still SILVER (upper bound of the band)")
        void tenIsSilver() {
            assertEquals(LoyaltyTier.SILVER, LoyaltyTier.forCount(10));
        }

        @Test
        @DisplayName("11 fulfilled orders crosses into GOLD (lower bound)")
        void elevenIsGold() {
            assertEquals(LoyaltyTier.GOLD, LoyaltyTier.forCount(11));
        }

        @Test
        @DisplayName("a large count stays GOLD")
        void largeCountIsGold() {
            assertEquals(LoyaltyTier.GOLD, LoyaltyTier.forCount(10_000));
        }

        @Test
        @DisplayName("throws IllegalArgumentException for a negative count")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> LoyaltyTier.forCount(-1));
        }
    }
}
