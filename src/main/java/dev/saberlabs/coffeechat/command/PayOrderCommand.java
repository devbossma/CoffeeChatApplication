package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.facade.OrderStateConflictException;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.service.PaymentService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Collects payment through the provider's Adapter and persists the outcome.
 *
 * <p>The gateway declining is <em>not an exception</em>: the command commits a {@code FAILED}
 * payment row and exposes the {@link PaymentResult} for the caller to act on. (Throwing would roll
 * the transaction back and lose the FAILED row; a {@code REQUIRES_NEW} step to save it first would
 * need a second pooled connection per in-flight payment and can deadlock a saturated pool.)
 *
 * <p>The order row is locked for the duration, so two concurrent payers are serialised before the
 * gateway is called. Only a READY order is payable, and a PAID order is never charged again.
 */
public class PayOrderCommand implements OrderCommand {

    private final Long orderId;
    private final PaymentProvider provider;
    private final PaymentGatewayResolver gateways;
    private final OrderService orders;
    private final PaymentService payments;
    private PaymentResult result;

    public PayOrderCommand(@NotNull Long orderId,
                           @NotNull PaymentProvider provider,
                           @NotNull PaymentGatewayResolver gateways,
                           @NotNull OrderService orders,
                           @NotNull PaymentService payments) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.gateways = Objects.requireNonNull(gateways, "gateways cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.payments = Objects.requireNonNull(payments, "payments cannot be null");
    }

    @Override
    public void execute() {
        OrderEntity order = orders.requireForPayment(orderId);
        if (order.status() != OrderStatus.READY) {
            throw new OrderStateConflictException(
                    "Order " + orderId + " cannot be paid while it is " + order.status() + "; only a READY order is payable");
        }
        result = payments.charge(order, provider, gateways.forProvider(provider));
    }

    /** Not supported: there is no refund flow (PRD §4), and a payment cannot be quietly forgotten. */
    @Override
    public void undo() {
        throw new UndoNotSupportedException("A payment cannot be undone: refunds are not modelled");
    }

    @Override
    public String name() {
        return "PayOrder";
    }

    /** The gateway outcome (PAID or FAILED) from the last {@link #execute()} that committed, or {@code null}. */
    public PaymentResult result() {
        return result;
    }
}
