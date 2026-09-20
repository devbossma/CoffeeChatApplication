package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.service.OrderService;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Collects payment through the provider's Adapter. The amount is always the order's persisted
 * {@code price().total()}, never a caller-supplied value. (Step 3c reshapes this to persist the
 * outcome and return a result instead of throwing.)
 */
public class PayOrderCommand implements OrderCommand {

    private final Long orderId;
    private final PaymentProvider provider;
    private final PaymentGatewayResolver gateways;
    private final OrderService orders;
    private PaymentResult result;

    public PayOrderCommand(@NotNull Long orderId,
                           @NotNull PaymentProvider provider,
                           @NotNull PaymentGatewayResolver gateways,
                           @NotNull OrderService orders) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.gateways = Objects.requireNonNull(gateways, "gateways cannot be null");
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
    }

    @Override
    public void execute() {
        var total = orders.require(orderId).price().total();
        result = gateways.forProvider(provider).pay("ORDER-" + orderId, total);
        if (!result.isPaid()) {
            throw new PaymentFailedException(result);
        }
    }

    @Override
    public void undo() {
        // No real processor to call; a refund would be issued here (see PRD §4).
        result = null;
    }

    @Override
    public String name() {
        return "PayOrder";
    }

    /** The gateway outcome from the last successful {@link #execute()}, or {@code null}. */
    public PaymentResult result() {
        return result;
    }
}
