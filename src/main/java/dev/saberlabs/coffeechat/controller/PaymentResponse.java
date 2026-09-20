package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;

import java.math.BigDecimal;

/** Response body of the pay endpoint: the normalised payment outcome (also the body of a 402 decline). */
public record PaymentResponse(PaymentProvider provider, String orderRef, BigDecimal amount, PaymentStatus status, String detail) {

    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(result.provider(), result.orderRef(), result.amount(), result.status(), result.detail());
    }
}
