/**
 * Pattern: ADAPTER.
 *
 * <p>A common {@code PaymentGateway} interface wrapping payment-provider-specific APIs
 * (PayPal / Stripe / Cash), each its own {@code @Component}, injected into whichever service
 * needs to take payment. See {@code PRD.md} section 7.2, row 7.
 */
package dev.saberlabs.coffeechat.adapter;
