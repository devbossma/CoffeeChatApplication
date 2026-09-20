package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.model.Order;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Pattern: PRODUCER-CONSUMER (Part 02) &mdash; the consumer.
 *
 * <p>{@code MyDesignPattern}'s {@code Barista} was a {@code Runnable} on a hand-started
 * {@code Thread}, with a {@code volatile running} flag for graceful shutdown. Here the same
 * consumer loop is an {@code @Async} method: {@link BaristaSupervisor} invokes
 * {@link #consumeLoop()} once per pool slot on startup, so N concurrent invocations of this one
 * bean's method give N real consumer loops without needing N separate {@code Barista} objects.
 *
 * <p>Per the {@code CLAUDE.md} hard rule, this class never touches {@code OrderService},
 * {@code OrderInvoker}, or a {@code Command} directly &mdash; every order it finishes preparing
 * goes back through {@link CoffeeShopFacade#prepareOrder(Long)}, the same door every other entry
 * point uses.
 */
@Service
public class Barista {

    private static final Logger log = LoggerFactory.getLogger(Barista.class);

    /** How long an idle barista waits for an order before re-checking whether it has been told to stop. */
    static final long POLL_TIMEOUT_MS = 200;

    private final OrderQueue orderQueue;
    private final CoffeeShopFacade facade;
    private volatile boolean running = true;

    public Barista(@NotNull OrderQueue orderQueue, @NotNull CoffeeShopFacade facade) {
        this.orderQueue = Objects.requireNonNull(orderQueue, "orderQueue cannot be null");
        this.facade = Objects.requireNonNull(facade, "facade cannot be null");
    }

    /**
     * Waits on {@link OrderQueue#poll} (a short timeout, not an indefinite {@code take()}) and, for
     * each dequeued order, prepares it via the facade. Runs until {@link #shutdown()} is called or
     * the thread is interrupted. The timed poll is what makes shutdown deterministic: an idle loop
     * notices {@code running == false} within {@link #POLL_TIMEOUT_MS} on its own, without relying
     * on anything interrupting a blocked thread.
     *
     * <p>A failure preparing one order (a bad state transition, a missing template, ...) is
     * logged and does not stop the loop &mdash; one bad order must not take an entire barista
     * offline.
     */
    @Async("baristaTaskExecutor")
    public void consumeLoop() {
        String barista = Thread.currentThread().getName();
        log.info("{} started", barista);
        while (running) {
            Order order;
            try {
                order = orderQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (order == null) {
                continue;
            }
            try {
                facade.prepareOrder(order.id());
                log.info("{} prepared order {}", barista, order.id());
            } catch (RuntimeException e) {
                log.error("{} failed to prepare order {}: {}", barista, order.id(), e.getMessage(), e);
            }
        }
        log.info("{} stopped", barista);
    }

    /** Signals the consumer loop to stop after its current wait/order completes. */
    public void shutdown() {
        running = false;
    }

    /** Re-arms the loop after {@link #shutdown()}, for a paused Spring context that is resumed. */
    public void restart() {
        running = true;
    }

    /** For tests/monitoring: whether {@link #shutdown()} has been called. */
    public boolean isRunning() {
        return running;
    }
}
