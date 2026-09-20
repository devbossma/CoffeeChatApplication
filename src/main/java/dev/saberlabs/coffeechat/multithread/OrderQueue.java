package dev.saberlabs.coffeechat.multithread;

import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * A bounded, thread-safe queue of the <em>ids</em> of placed orders awaiting preparation.
 *
 * <p>It carries ids, not order objects: the database is the only holder of order state, so a
 * queued mutable object would be a second, stale copy. The consumer reloads the order by id inside
 * its own transaction. The queue itself is coordination state (what to work on next), not history,
 * which is why it legitimately stays in memory.
 *
 * <p>{@code MyDesignPattern}'s {@code OrderQueue} hand-rolled a fair {@code ReentrantLock} with
 * two {@code Condition}s; the JDK's {@link ArrayBlockingQueue} already is that design, so this
 * wraps it (PRD &sect;8).
 *
 * <p><b>Dedupe.</b> An id that is already waiting is not enqueued a second time (restart recovery
 * and a live placement can race). An id is forgotten the moment it is taken or polled, so a
 * legitimate re-enqueue after a take (the barista's retry path) is never blocked by a stale entry.
 * This is an efficiency measure only: correctness never depends on it, because preparing an order
 * that is no longer preparable is a no-op for the consumer.
 */
@Component
public class OrderQueue {

    private final BlockingQueue<Long> queue;
    private final Set<Long> waiting = ConcurrentHashMap.newKeySet();

    public OrderQueue(@Value("${coffeeshop.order-queue-capacity:10}") int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Order queue capacity must be at least 1: " + capacity);
        }
        this.queue = new ArrayBlockingQueue<>(capacity, true);
    }

    /**
     * Adds {@code orderId} to the queue, blocking the caller while the queue is at capacity. A no-op
     * if the id is already waiting.
     *
     * @throws IllegalStateException if the calling thread is interrupted while waiting for space;
     *                                the thread's interrupt status is restored before throwing
     */
    public void enqueue(@NotNull Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        if (!waiting.add(orderId)) {
            return;
        }
        try {
            queue.put(orderId);
        } catch (InterruptedException e) {
            waiting.remove(orderId);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueueing order " + orderId, e);
        }
    }

    /**
     * Removes and returns the next order id, blocking the caller while the queue is empty.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public Long take() throws InterruptedException {
        Long id = queue.take();
        waiting.remove(id);
        return id;
    }

    /**
     * Removes and returns the next order id, waiting up to {@code timeout} for one to arrive.
     *
     * @return the next id, or {@code null} if none arrived within the timeout
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public Long poll(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit cannot be null");
        Long id = queue.poll(timeout, unit);
        if (id != null) {
            waiting.remove(id);
        }
        return id;
    }

    public boolean contains(Long orderId) {
        return queue.contains(orderId);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Free slots left before {@link #enqueue(Long)} would block. */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
