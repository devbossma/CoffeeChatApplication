package dev.saberlabs.coffeechat.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Payment adaptees (simulated third-party services)")
class PaymentServiceTest {

    @Nested
    @DisplayName("PayPalPaymentService.makePayment()")
    class PayPalTests {

        @Test
        @DisplayName("accepts an amount within the balance")
        void withinBalance() {
            assertTrue(new PayPalPaymentService(1_000L).makePayment(999L, "ref"));
        }

        @Test
        @DisplayName("rejects an amount over the balance")
        void overBalance() {
            assertFalse(new PayPalPaymentService(1_000L).makePayment(1_001L, "ref"));
        }

        @Test
        @DisplayName("rejects a non-positive amount")
        void nonPositive() {
            assertFalse(new PayPalPaymentService().makePayment(0L, "ref"));
        }
    }

    @Nested
    @DisplayName("StripePaymentService.charge()")
    class StripeTests {

        @Test
        @DisplayName("charges a well-formed card")
        void validCard() {
            assertTrue(new StripePaymentService().charge(350L, "ref"));
        }

        @Test
        @DisplayName("refuses a card number that is not 16 digits")
        void badNumber() {
            assertFalse(new StripePaymentService("123", "123").charge(350L, "ref"));
        }

        @Test
        @DisplayName("refuses a CVC that is not 3 digits")
        void badCvc() {
            assertFalse(new StripePaymentService("4242424242424242", "12").charge(350L, "ref"));
        }

        @Test
        @DisplayName("refuses a non-positive amount even with a valid card")
        void nonPositiveAmount() {
            assertFalse(new StripePaymentService().charge(0L, "ref"));
        }
    }

    @Nested
    @DisplayName("CashPaymentService.collectCash()")
    class CashTests {

        @Test
        @DisplayName("returns the change when enough was tendered")
        void returnsChange() {
            BigDecimal change = new CashPaymentService()
                    .collectCash(new BigDecimal("3.50"), new BigDecimal("5.00"));
            assertEquals(new BigDecimal("1.50"), change);
        }

        @Test
        @DisplayName("returns zero change on exact payment")
        void exactPayment() {
            BigDecimal change = new CashPaymentService()
                    .collectCash(new BigDecimal("3.50"), new BigDecimal("3.50"));
            assertEquals(0, BigDecimal.ZERO.compareTo(change));
        }

        @Test
        @DisplayName("returns a negative sentinel when too little was tendered")
        void insufficient() {
            BigDecimal change = new CashPaymentService()
                    .collectCash(new BigDecimal("3.50"), new BigDecimal("2.00"));
            assertTrue(change.signum() < 0);
        }
    }
}
