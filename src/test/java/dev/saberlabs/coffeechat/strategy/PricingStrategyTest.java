package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PricingStrategy")
class PricingStrategyTest {

    private final PricingStrategy regular = new RegularPricing();
    private final PricingStrategy silver = new SilverMemberPricing();
    private final PricingStrategy gold = new GoldMemberPricing();

    @Nested
    @DisplayName("supportedTier()")
    class SupportedTierTests {

        @Test
        @DisplayName("each strategy names the tier it prices for")
        void namesTier() {
            assertEquals(LoyaltyTier.REGULAR, regular.supportedTier());
            assertEquals(LoyaltyTier.SILVER, silver.supportedTier());
            assertEquals(LoyaltyTier.GOLD, gold.supportedTier());
        }
    }

    @Nested
    @DisplayName("priceFor()")
    class PriceForTests {

        @Test
        @DisplayName("REGULAR charges the base cost unchanged")
        void regularNoDiscount() {
            assertEquals(new BigDecimal("4.00"), regular.priceFor(new BigDecimal("4.00")));
        }

        @Test
        @DisplayName("SILVER takes 10% off")
        void silverTenPercent() {
            assertEquals(new BigDecimal("3.60"), silver.priceFor(new BigDecimal("4.00")));
        }

        @Test
        @DisplayName("GOLD takes 20% off")
        void goldTwentyPercent() {
            assertEquals(new BigDecimal("3.20"), gold.priceFor(new BigDecimal("4.00")));
        }

        @Test
        @DisplayName("the result is always rounded to 2 decimal places")
        void roundedToCents() {
            // 3.75 * 0.90 = 3.375 -> 3.38
            assertEquals(new BigDecimal("3.38"), silver.priceFor(new BigDecimal("3.75")));
        }
    }

    @Nested
    @DisplayName("discountAmount()")
    class DiscountAmountTests {

        @Test
        @DisplayName("REGULAR discounts nothing")
        void regularZero() {
            assertEquals(0, new BigDecimal("0.00").compareTo(regular.discountAmount(new BigDecimal("4.00"))));
        }

        @Test
        @DisplayName("GOLD discounts base minus the discounted price")
        void goldDifference() {
            assertEquals(0, new BigDecimal("0.80").compareTo(gold.discountAmount(new BigDecimal("4.00"))));
        }
    }
}
