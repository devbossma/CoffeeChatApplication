package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    private final OrderQueue orderQueue;
    private final CoffeeShopFacade facade;
    private volatile boolean running = true;

    public Barista(OrderQueue orderQueue, CoffeeShopFacade facade) {
        this.orderQueue = orderQueue;
        this.facade = facade;
    }

    /**
     * Blocks on {@link OrderQueue#take()} and, for each dequeued order, prepares it via the
     * facade. Runs until {@link #shutdown()} is called or the thread is interrupted (e.g. by the
     * executor's shutdown, which interrupts blocked tasks &mdash; see {@code AsyncConfig}).
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
                order = orderQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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

    /** For tests/monitoring: whether {@link #shutdown()} has been called. */
    public boolean isRunning() {
        return running;
    }
}
