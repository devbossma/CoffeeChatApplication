package dev.saberlabs.coffeechat.adapter;

/**
 * Which payment back-end an order was paid through. Keys the {@link PaymentGatewayResolver}.
 */
public enum PaymentProvider {
    PAYPAL,
    STRIPE,
    CASH
}
