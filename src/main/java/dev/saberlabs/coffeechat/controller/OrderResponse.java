package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Response body for order endpoints. */
public record OrderResponse(Long id,
                            Long customerId,
                            String coffee,
                            CoffeeType baseType,
                            List<ExtraType> extras,
                            BigDecimal priceBase,
                            BigDecimal priceExtras,
                            BigDecimal priceDiscount,
                            BigDecimal priceTotal,
                            LoyaltyTier appliedLoyaltyTier,
                            OrderStatus status,
                            Instant placedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customer().id(),
                order.coffee().description(),
                order.baseType(),
                order.extras(),
                order.price().base(),
                order.price().extras(),
                order.price().discount(),
                order.price().total(),
                order.appliedLoyaltyTier(),
                order.status(),
                order.placedAt());
    }
}
