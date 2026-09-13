package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.Order;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A bounded, thread-safe queue of placed orders awaiting preparation.
 *
 * <p>{@code MyDesignPattern}'s {@code OrderQueue} hand-rolled a fair {@code ReentrantLock} with
 * two {@code Condition}s (one for "not full", one for "not empty") to get exactly this behaviour.
 * The JDK's {@link ArrayBlockingQueue} already <em>is</em> that design internally — including the
 * optional fair-ordering constructor — so this component wraps it instead of reimplementing it,
 * per PRD &sect;8 ("keep OrderQueue as a thread-safe BlockingQueue&lt;Order&gt;-backed component").
 *
 * <p>{@link #enqueue(Order)} blocks the calling (producer) thread while the queue is full;
 * {@link #take()} blocks the calling (consumer/Barista) thread while it is empty. Capacity is
 * fixed at construction from {@code coffeeshop.order-queue-capacity}.
 */
@Component
public class OrderQueue {

    private final BlockingQueue<Order> queue;

    public OrderQueue(@Value("${coffeeshop.order-queue-capacity:10}") int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Order queue capacity must be at least 1: " + capacity);
        }
        this.queue = new ArrayBlockingQueue<>(capacity, true);
    }

    /**
     * Adds {@code order} to the queue, blocking the caller while the queue is at capacity.
     *
     * @throws IllegalStateException if the calling thread is interrupted while waiting for space;
     *                                the thread's interrupt status is restored before throwing
     */
    public void enqueue(@NotNull Order order) {
        Objects.requireNonNull(order, "order cannot be null");
        try {
            queue.put(order);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while enqueueing order " + order.id(), e);
        }
    }

    /**
     * Removes and returns the next order, blocking the caller while the queue is empty.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting; the
     *                               thread's interrupt status is left set by the JDK as usual
     */
    public Order take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** Free slots left before {@link #enqueue(Order)} would block. */
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
