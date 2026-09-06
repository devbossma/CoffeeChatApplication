package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.LoyaltyTier;

/** Response body for customer endpoints. */
public record CustomerResponse(Long id, String name, long fulfilledOrders, LoyaltyTier loyaltyTier) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.id(), customer.name(), customer.fulfilledOrders(), customer.loyaltyTier());
    }
}
