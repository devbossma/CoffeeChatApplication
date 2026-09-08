package dev.saberlabs.coffeechat.strategy;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pattern 4: STRATEGY &mdash; {@link LoyaltyTier#REGULAR}: no discount (0%).
 */
@Component
public class RegularPricing extends PercentageDiscountPricing {

    public RegularPricing() {
        super(LoyaltyTier.REGULAR, BigDecimal.ZERO);
    }
}
