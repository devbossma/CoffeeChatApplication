package dev.saberlabs.coffeechat.adapter;

/**
 * Normalised payment outcome that every adapter maps its provider-specific response to, so the
 * rest of the system never depends on a provider's return type. Matches the {@code status}
 * column the Part 03 {@code PaymentEntity} will carry ({@code CLAUDE.md}).
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED
}
