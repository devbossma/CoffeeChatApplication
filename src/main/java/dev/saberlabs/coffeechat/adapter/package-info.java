/**
 * Pattern: ADAPTER.
 *
 * <p>A common {@code PaymentGateway} interface wrapping payment-provider-specific APIs
 * (PayPal / Stripe / Cash), each its own {@code @Component}, injected into whichever service
 * needs to take payment.
 */
package dev.saberlabs.coffeechat.adapter;
