package dev.saberlabs.coffeechat.command;

import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.model.Order;

/**
 * Pattern 6: COMMAND &mdash; collect payment for the order through the Adapter selected by
 * {@link PaymentProvider}. Does not change the order status (payment is orthogonal to the
 * prep lifecycle, as in {@code MyDesignPattern}); it throws {@link PaymentFailedException} if the
 * gateway declines. {@code undo()} is a best-effort refund note.
 *
 * <p>Part 03 persists the {@link PaymentResult} as a {@code PaymentEntity}; here it is kept on
 * the command for the caller to read.
 */
public class PayOrderCommand implements OrderCommand {

    private final Order order;
    private final PaymentProvider provider;
    private final PaymentGatewayResolver gateways;
    private PaymentResult result;

    public PayOrderCommand(Order order, PaymentProvider provider, PaymentGatewayResolver gateways) {
        this.order = order;
        this.provider = provider;
        this.gateways = gateways;
    }

    @Override
    public void execute() {
        String orderRef = "ORDER-" + order.id();
        result = gateways.forProvider(provider).pay(orderRef, order.price().total());
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
