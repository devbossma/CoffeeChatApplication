package dev.saberlabs.coffeechat.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/customers}. */
public record CreateCustomerRequest(@NotBlank @Size(max = 255) String name) {
}
