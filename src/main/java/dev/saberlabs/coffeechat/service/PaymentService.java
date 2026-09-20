package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.adapter.PaymentGateway;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.adapter.PaymentStatus;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.PaymentEntity;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.repository.PaymentRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Payment rules over {@link PaymentRepository}. Every method requires the caller's transaction
 * (the order lifecycle command), so a payment row commits or rolls back with everything else the
 * command does.
 *
 * <p>One payment row per order ({@code UNIQUE(order_id)}) holding the order's <em>current</em>
 * payment state: a FAILED row is updated by a retry, a PAID row is final. The amount charged is
 * always the order's persisted total, never a caller-supplied value.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;

    public PaymentService(@NotNull PaymentRepository payments) {
        this.payments = Objects.requireNonNull(payments, "payments cannot be null");
    }

    /**
     * Charges {@code order}'s total through {@code gateway} and records the outcome. The caller has
     * already locked the order row ({@code OrderService.requireForPayment}), so concurrent payers are
     * serialised before this runs.
     *
     * @return the gateway's result, PAID or FAILED; a decline is a result, not an exception
     * @throws OrderStateConflictException if the order is already PAID (the gateway is NOT called)
     * @throws IllegalStateException       if the gateway reports an amount different from the order's total
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentResult charge(@NotNull OrderEntity order, @NotNull PaymentProvider provider, @NotNull PaymentGateway gateway) {
        Objects.requireNonNull(order, "order cannot be null");
        Objects.requireNonNull(provider, "provider cannot be null");
        Objects.requireNonNull(gateway, "gateway cannot be null");

        Optional<PaymentEntity> existing = payments.findByOrderId(order.id());
        if (existing.isPresent() && existing.get().status() == PaymentStatus.PAID) {
            throw new OrderStateConflictException("Order " + order.id() + " is already paid");
        }

        BigDecimal total = order.price().total();
        PaymentResult result = gateway.pay("ORDER-" + order.id(), total);
        if (result.amount().compareTo(total) != 0) {
            throw new IllegalStateException("Gateway " + provider + " charged " + result.amount()
                    + " for order " + order.id() + " whose total is " + total);
        }

        Instant now = Instant.now();
        if (existing.isPresent()) {
            existing.get().recordAttempt(provider, result.status(), result.detail(), now);
        } else {
            try {
                payments.saveAndFlush(new PaymentEntity(order, provider, total, result.status(), result.detail(), now, now));
            } catch (DataIntegrityViolationException e) {
                // Backstop for the unique constraint; the row lock normally makes this unreachable.
                throw new OrderStateConflictException("Order " + order.id() + " is already paid");
            }
        }
        return result;
    }

    /**
     * @throws OrderStateConflictException if the order has no PAID payment
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentEntity requirePaid(@NotNull Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return payments.findByOrderId(orderId)
                .filter(p -> p.status() == PaymentStatus.PAID)
                .orElseThrow(() -> new OrderStateConflictException(
                        "Order " + orderId + " has not been paid, so it cannot be fulfilled"));
    }
}
