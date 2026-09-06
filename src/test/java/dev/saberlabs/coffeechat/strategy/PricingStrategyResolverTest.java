package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PricingStrategyResolver")
class PricingStrategyResolverTest {

    private static List<PricingStrategy> allThree() {
        return List.of(new RegularPricing(), new SilverMemberPricing(), new GoldMemberPricing());
    }

    /** A stand-in strategy that lets a test force a duplicate tier. */
    private record FakeStrategy(LoyaltyTier supportedTier) implements PricingStrategy {
        @Override
        public BigDecimal priceFor(BigDecimal baseCost) {
            return baseCost;
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts exactly one strategy per tier")
        void acceptsFullSet() {
            PricingStrategyResolver resolver = new PricingStrategyResolver(allThree());
            assertInstanceOf(GoldMemberPricing.class, resolver.forTier(LoyaltyTier.GOLD));
        }

        @Test
        @DisplayName("rejects two strategies claiming the same tier")
        void rejectsDuplicateTier() {
            List<PricingStrategy> withDupe = List.of(
                    new RegularPricing(), new SilverMemberPricing(), new GoldMemberPricing(),
                    new FakeStrategy(LoyaltyTier.GOLD));
            assertThrows(IllegalStateException.class, () -> new PricingStrategyResolver(withDupe));
        }

        @Test
        @DisplayName("rejects a set that is missing a tier")
        void rejectsMissingTier() {
            List<PricingStrategy> missingGold = List.of(new RegularPricing(), new SilverMemberPricing());
            assertThrows(IllegalStateException.class, () -> new PricingStrategyResolver(missingGold));
        }
    }

    @Nested
    @DisplayName("forTier()")
    class ForTierTests {

        private final PricingStrategyResolver resolver = new PricingStrategyResolver(allThree());

        @Test
        @DisplayName("returns the strategy registered for the tier")
        void returnsRegistered() {
            assertInstanceOf(SilverMemberPricing.class, resolver.forTier(LoyaltyTier.SILVER));
        }

        @Test
        @DisplayName("returns the same singleton strategy instance each call")
        void stableInstance() {
            assertSame(resolver.forTier(LoyaltyTier.REGULAR), resolver.forTier(LoyaltyTier.REGULAR));
        }

        @Test
        @DisplayName("throws for a null tier")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> resolver.forTier(null));
        }
    }
}
