/**
 * Pattern: ADAPTER.
 *
 * <p>A common {@code PaymentGateway} target interface (dollars in, a normalised
 * {@code PaymentResult} out) wrapping three incompatible simulated back-ends &mdash;
 * {@code PayPalPaymentService} / {@code StripePaymentService} / {@code CashPaymentService}. Each
 * adapter is a {@code @Component} that declares its {@code PaymentProvider}; a
 * {@code PaymentGatewayResolver} builds the provider &rarr; gateway {@code EnumMap} once, failing
 * fast on a duplicate or missing provider. No real payment processor is contacted (PRD &sect;4).
 */
package dev.saberlabs.coffeechat.adapter;
