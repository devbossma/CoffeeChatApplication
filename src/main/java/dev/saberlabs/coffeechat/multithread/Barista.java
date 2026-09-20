package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.model.IllegalOrderTransitionException;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
 * goes back through {@link CoffeeShopFacade#prepareOrder(Long, Actor)} as {@link Actor#SYSTEM} (the loops are automation, not a person, so no {@code changed_by}), the same door every other entry
 * point uses. The consumer thread is not inside a transaction; each facade call opens its own.
 */
@Service
public class Barista {

    private static final Logger log = LoggerFactory.getLogger(Barista.class);

    /** How long an idle barista waits for an order before re-checking whether it has been told to stop. */
    static final long POLL_TIMEOUT_MS = 200;

    /** Attempts at one order when a concurrent writer wins the {@code @Version} race. */
    static final int MAX_ATTEMPTS = 3;

    /**
     * Total attempts at one order that keeps failing unexpectedly (each retry is a delayed re-enqueue,
     * so another barista may take it). After the last one the order is left PLACED for startup recovery.
     */
    static final int MAX_UNEXPECTED_ATTEMPTS = 3;

    private final OrderQueue orderQueue;
    private final CoffeeShopFacade facade;
    private final long retryDelayMs;
    private final Map<Long, Integer> unexpectedAttempts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "barista-retry");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running = true;

    @Autowired
    public Barista(@NotNull OrderQueue orderQueue,
                   @NotNull CoffeeShopFacade facade,
                   @Value("${coffeeshop.barista-retry-delay-ms:200}") long retryDelayMs) {
        this.orderQueue = Objects.requireNonNull(orderQueue, "orderQueue cannot be null");
        this.facade = Objects.requireNonNull(facade, "facade cannot be null");
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retry delay cannot be negative: " + retryDelayMs);
        }
        this.retryDelayMs = retryDelayMs;
    }

    public Barista(@NotNull OrderQueue orderQueue, @NotNull CoffeeShopFacade facade) {
        this(orderQueue, facade, 200);
    }

    /**
     * Waits on {@link OrderQueue#poll} (a short timeout, not an indefinite {@code take()}) and, for
     * each dequeued order id, prepares it via the facade. Runs until {@link #shutdown()} is called or
     * the thread is interrupted. The timed poll is what makes shutdown deterministic: an idle loop
     * notices {@code running == false} within {@link #POLL_TIMEOUT_MS} on its own, without relying
     * on anything interrupting a blocked thread.
     *
     * <p>A failure preparing one order is logged and does not stop the loop &mdash; one bad order
     * must not take an entire barista offline.
     */
    @Async("baristaTaskExecutor")
    public void consumeLoop() {
        String barista = Thread.currentThread().getName();
        log.info("{} started", barista);
        while (running) {
            Long orderId;
            try {
                orderId = orderQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (orderId == null) {
                continue;
            }
            try {
                prepare(barista, orderId);
            } catch (RuntimeException e) {
                // Belt and braces: nothing may escape and end this loop, or that barista is gone for good.
                log.error("{} hit an unhandled failure on order {}; continuing", barista, orderId, e);
            }
        }
        log.info("{} stopped", barista);
    }

    /**
     * One order, with the concurrency policy: a lost {@code @Version} race is retried (the retry
     * reloads, so it sees whatever the other writer did); an order that is gone or no longer
     * preparable (cancelled by a REST call, already prepared by another barista, or an id that was
     * never visible) is skipped quietly, since that is a normal outcome and not an error.
     */
    private void prepare(String barista, Long orderId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                facade.prepareOrder(orderId, Actor.SYSTEM);
                unexpectedAttempts.remove(orderId);
                log.info("{} prepared order {}", barista, orderId);
                return;
            } catch (OptimisticLockingFailureException e) {
                log.warn("{} lost a concurrent update on order {} (attempt {}/{})", barista, orderId, attempt, MAX_ATTEMPTS);
            } catch (OrderNotFoundException e) {
                unexpectedAttempts.remove(orderId);
                log.warn("{} skipped order {}: not found", barista, orderId);
                return;
            } catch (IllegalOrderTransitionException e) {
                // Only this specific outcome is a quiet skip. Any other IllegalStateException (for
                // instance the publisher's "no transaction" error) is a real bug and must not hide here.
                unexpectedAttempts.remove(orderId);
                log.info("{} skipped order {}: {}", barista, orderId, e.getMessage());
                return;
            } catch (RuntimeException e) {
                requeueAfterUnexpectedFailure(barista, orderId, e);
                return;
            }
        }
        // Every attempt lost the @Version race: the id must not be dropped either.
        requeueAfterUnexpectedFailure(barista, orderId, new OptimisticLockingFailureException(
                "lost the concurrent-update race " + MAX_ATTEMPTS + " times in a row"));
    }

    /**
     * An unexpected failure (not a lost race, not a gone/finished order) would otherwise drop the id
     * and leave the order PLACED until the next restart. Re-enqueue it after a short delay, up to
     * {@link #MAX_UNEXPECTED_ATTEMPTS} attempts in total (the queue's dedupe set still applies, so a
     * concurrent recovery enqueue cannot double it), then log ERROR and leave it for startup recovery.
     */
    private void requeueAfterUnexpectedFailure(String barista, Long orderId, RuntimeException cause) {
        int attempt = unexpectedAttempts.merge(orderId, 1, Integer::sum);
        if (attempt >= MAX_UNEXPECTED_ATTEMPTS) {
            unexpectedAttempts.remove(orderId);
            log.error("{} gave up on order {} after {} unexpected failures; it stays PLACED until startup recovery: {}",
                    barista, orderId, attempt, cause.getMessage(), cause);
            return;
        }
        log.warn("{} failed unexpectedly on order {} (attempt {}/{}), re-queueing: {}",
                barista, orderId, attempt, MAX_UNEXPECTED_ATTEMPTS, cause.getMessage());
        try {
            retryScheduler.schedule(() -> reenqueue(orderId), retryDelayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The scheduler is already shut down (the application is closing): the order stays PLACED
            // and startup recovery will pick it up.
            log.error("{} could not schedule a retry for order {} (shutting down); it stays PLACED for startup recovery",
                    barista, orderId);
        }
    }

    private void reenqueue(Long orderId) {
        try {
            orderQueue.enqueue(orderId);
        } catch (RuntimeException e) {
            log.error("Could not re-enqueue order {} for retry; it stays PLACED until startup recovery", orderId, e);
        }
    }

    @PreDestroy
    void closeRetryScheduler() {
        retryScheduler.shutdownNow();
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
