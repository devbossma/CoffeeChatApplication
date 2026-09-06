package dev.saberlabs.coffeechat.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PaymentResult")
class PaymentResultTest {

    @Nested
    @DisplayName("isPaid()")
    class IsPaidTests {

        @Test
        @DisplayName("true for a PAID result")
        void trueWhenPaid() {
            PaymentResult r = PaymentResult.paid(PaymentProvider.CASH, "O-1", new BigDecimal("2.50"), "ok");
            assertTrue(r.isPaid());
        }

        @Test
        @DisplayName("false for a FAILED result")
        void falseWhenFailed() {
            PaymentResult r = PaymentResult.failed(PaymentProvider.CASH, "O-1", new BigDecimal("2.50"), "no");
            assertFalse(r.isPaid());
        }
    }
}
