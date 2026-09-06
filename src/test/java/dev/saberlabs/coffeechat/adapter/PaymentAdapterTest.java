package dev.saberlabs.coffeechat.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Payment adapters")
class PaymentAdapterTest {

    @Nested
    @DisplayName("pay() argument validation (AbstractPaymentAdapter)")
    class ValidationTests {

        private final PaymentGateway gateway = new PayPalAdapter();

        @Test
        @DisplayName("rejects a blank order reference")
        void rejectsBlankRef() {
            assertThrows(IllegalArgumentException.class, () -> gateway.pay("  ", new BigDecimal("3.00")));
        }

        @Test
        @DisplayName("rejects a null amount")
        void rejectsNullAmount() {
            assertThrows(IllegalArgumentException.class, () -> gateway.pay("ORDER-1", null));
        }

        @Test
        @DisplayName("rejects a zero or negative amount")
        void rejectsNonPositiveAmount() {
            assertThrows(IllegalArgumentException.class, () -> gateway.pay("ORDER-1", BigDecimal.ZERO));
            assertThrows(IllegalArgumentException.class, () -> gateway.pay("ORDER-1", new BigDecimal("-1.00")));
        }
    }

    @Nested
    @DisplayName("PayPalAdapter")
    class PayPalAdapterTests {

        @Test
        @DisplayName("fronts the PAYPAL provider")
        void provider() {
            assertEquals(PaymentProvider.PAYPAL, new PayPalAdapter().provider());
        }

        @Test
        @DisplayName("clears a payment the balance can cover")
        void clearsPayment() {
            PaymentResult result = new PayPalAdapter().pay("ORDER-1", new BigDecimal("4.00"));
            assertTrue(result.isPaid());
            assertEquals(PaymentProvider.PAYPAL, result.provider());
        }

        @Test
        @DisplayName("declines when the balance is too low")
        void declinesWhenShort() {
            PaymentGateway broke = new PayPalAdapter(new PayPalPaymentService(100L)); // $1.00 balance
            assertFalse(broke.pay("ORDER-1", new BigDecimal("4.00")).isPaid());
        }

        @Test
        @DisplayName("converts dollars to cents before calling PayPal")
        void convertsToCents() {
            // balance is exactly 400 cents; $4.00 must map to 400, not 4
            PaymentGateway exact = new PayPalAdapter(new PayPalPaymentService(400L));
            assertTrue(exact.pay("ORDER-1", new BigDecimal("4.00")).isPaid());
            PaymentGateway justUnder = new PayPalAdapter(new PayPalPaymentService(399L));
            assertFalse(justUnder.pay("ORDER-1", new BigDecimal("4.00")).isPaid());
        }
    }

    @Nested
    @DisplayName("StripeAdapter")
    class StripeAdapterTests {

        @Test
        @DisplayName("fronts the STRIPE provider")
        void provider() {
            assertEquals(PaymentProvider.STRIPE, new StripeAdapter().provider());
        }

        @Test
        @DisplayName("charges a valid card and prefixes the reference with STRIPE-")
        void chargesValidCard() {
            PaymentResult result = new StripeAdapter().pay("ORDER-7", new BigDecimal("3.50"));
            assertTrue(result.isPaid());
            assertTrue(result.detail().contains("STRIPE-ORDER-7"));
        }

        @Test
        @DisplayName("declines an invalid card")
        void declinesInvalidCard() {
            PaymentGateway badCard = new StripeAdapter(new StripePaymentService("not-a-number", "x"));
            assertFalse(badCard.pay("ORDER-7", new BigDecimal("3.50")).isPaid());
        }
    }

    @Nested
    @DisplayName("CashPaymentAdapter")
    class CashPaymentAdapterTests {

        @Test
        @DisplayName("fronts the CASH provider")
        void provider() {
            assertEquals(PaymentProvider.CASH, new CashPaymentAdapter().provider());
        }

        @Test
        @DisplayName("takes the next dollar up and reports the change")
        void reportsChange() {
            PaymentResult result = new CashPaymentAdapter().pay("ORDER-3", new BigDecimal("3.50"));
            assertTrue(result.isPaid());
            assertEquals("change $0.50", result.detail());
        }

        @Test
        @DisplayName("fails if the register reports insufficient cash")
        void failsOnInsufficient() {
            CashPaymentService alwaysShort = new CashPaymentService() {
                @Override
                public BigDecimal collectCash(BigDecimal amountDue, BigDecimal amountTendered) {
                    return CashPaymentService.INSUFFICIENT;
                }
            };
            PaymentGateway gateway = new CashPaymentAdapter(alwaysShort);
            assertFalse(gateway.pay("ORDER-3", new BigDecimal("3.50")).isPaid());
        }
    }
}
