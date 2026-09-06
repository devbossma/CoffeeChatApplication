package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.CoffeeType;

import java.util.Set;

/** Response body for the admin shop endpoints. */
public record ShopStateResponse(boolean open, Set<CoffeeType> menu) {
}
