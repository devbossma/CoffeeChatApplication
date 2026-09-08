package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pattern 4: STRATEGY &mdash; {@link LoyaltyTier#SILVER}: 10% off.
 */
@Component
public class SilverMemberPricing extends PercentageDiscountPricing {

    public SilverMemberPricing() {
        super(LoyaltyTier.SILVER, new BigDecimal("0.10"));
    }
}
