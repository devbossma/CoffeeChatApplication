package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pattern 4: STRATEGY &mdash; {@link LoyaltyTier#GOLD}: 20% off.
 */
@Component
public class GoldMemberPricing extends PercentageDiscountPricing {

    public GoldMemberPricing() {
        super(LoyaltyTier.GOLD, new BigDecimal("0.20"));
    }
}
