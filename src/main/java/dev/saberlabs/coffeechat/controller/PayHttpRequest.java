package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/orders/{id}/pay}. An unknown provider value is a 400 (unreadable body), never a 500. */
public record PayHttpRequest(@NotNull PaymentProvider provider) {
}
