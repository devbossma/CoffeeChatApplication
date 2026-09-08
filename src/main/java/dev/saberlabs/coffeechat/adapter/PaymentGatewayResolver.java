package dev.saberlabs.coffeechat.adapter;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link PaymentProvider} &rarr; {@link PaymentGateway} lookup from every adapter bean
 * Spring injects, keyed by each adapter's own {@link PaymentGateway#provider()} &mdash; the same
 * resolver-over-an-EnumMap shape used for pricing strategies and preparation templates.
 *
 * <p>Construction fails fast on a duplicate or missing provider.
 */
@Component
public class PaymentGatewayResolver {

    private final Map<PaymentProvider, PaymentGateway> byProvider;

    public PaymentGatewayResolver(List<PaymentGateway> gateways) {
        this.byProvider = new EnumMap<>(PaymentProvider.class);
        for (PaymentGateway gateway : gateways) {
            PaymentGateway previous = byProvider.put(gateway.provider(), gateway);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two payment gateways claim provider " + gateway.provider() + ": "
                                + previous.getClass().getSimpleName() + " and "
                                + gateway.getClass().getSimpleName());
            }
        }
        for (PaymentProvider provider : PaymentProvider.values()) {
            if (!byProvider.containsKey(provider)) {
                throw new IllegalStateException("No payment gateway registered for provider " + provider);
            }
        }
    }

    /**
     * @param provider the provider to pay through
     * @return the adapter fronting that provider
     * @throws IllegalArgumentException if {@code provider} is null
     */
    public PaymentGateway forProvider(PaymentProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Payment provider cannot be null");
        }
        return byProvider.get(provider);
    }
}
