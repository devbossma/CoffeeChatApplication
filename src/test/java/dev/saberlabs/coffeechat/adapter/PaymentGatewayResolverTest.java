package dev.saberlabs.coffeechat.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PaymentGatewayResolver")
class PaymentGatewayResolverTest {

    private static List<PaymentGateway> allThree() {
        return List.of(new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter());
    }

    private record FakeGateway(PaymentProvider provider) implements PaymentGateway {
        @Override
        public PaymentResult pay(String orderRef, BigDecimal amount) {
            return PaymentResult.paid(provider, orderRef, amount, "fake");
        }
    }

    @Nested
    @DisplayName("constructor")
    class ConstructorTests {

        @Test
        @DisplayName("accepts exactly one gateway per provider")
        void acceptsFullSet() {
            PaymentGatewayResolver resolver = new PaymentGatewayResolver(allThree());
            assertInstanceOf(StripeAdapter.class, resolver.forProvider(PaymentProvider.STRIPE));
        }

        @Test
        @DisplayName("rejects two gateways claiming the same provider")
        void rejectsDuplicate() {
            List<PaymentGateway> withDupe = List.of(
                    new PayPalAdapter(), new StripeAdapter(), new CashPaymentAdapter(),
                    new FakeGateway(PaymentProvider.CASH));
            assertThrows(IllegalStateException.class, () -> new PaymentGatewayResolver(withDupe));
        }

        @Test
        @DisplayName("rejects a set missing a provider")
        void rejectsMissing() {
            assertThrows(IllegalStateException.class,
                    () -> new PaymentGatewayResolver(List.of(new PayPalAdapter(), new StripeAdapter())));
        }
    }

    @Nested
    @DisplayName("forProvider()")
    class ForProviderTests {

        private final PaymentGatewayResolver resolver = new PaymentGatewayResolver(allThree());

        @Test
        @DisplayName("returns the gateway registered for the provider")
        void returnsRegistered() {
            assertInstanceOf(CashPaymentAdapter.class, resolver.forProvider(PaymentProvider.CASH));
        }

        @Test
        @DisplayName("returns the same instance each call")
        void stableInstance() {
            assertSame(resolver.forProvider(PaymentProvider.PAYPAL), resolver.forProvider(PaymentProvider.PAYPAL));
        }

        @Test
        @DisplayName("throws for a null provider")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> resolver.forProvider(null));
        }
    }
}
