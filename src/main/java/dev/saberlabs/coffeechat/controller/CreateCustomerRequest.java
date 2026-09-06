package dev.saberlabs.coffeechat.controller;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/customers}. */
public record CreateCustomerRequest(@NotBlank String name) {
}
