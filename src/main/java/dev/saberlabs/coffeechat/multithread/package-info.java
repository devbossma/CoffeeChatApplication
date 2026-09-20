/**
 * Producer-Consumer concurrency (Part 02).
 *
 * <p>{@code OrderQueue} is a bounded, thread-safe {@code BlockingQueue<Order>}-backed component.
 * The REST controller (via {@code CoffeeShopFacade.placeOrder}) is the producer — there is no
 * ported {@code CustomerThread}, since real HTTP request threads already fill that role.
 * {@code OrderQueueDispatcher} enqueues an order reactively, as an {@code @EventListener} on the
 * {@code OrderStatusChangedEvent} published when an order reaches {@code PLACED} — a second,
 * independent listener alongside the observer package's notification listener, not the same one
 * doing both jobs. {@code Barista} is the consumer: an {@code @Async} loop that waits on
 * a timed {@code OrderQueue.poll(...)} and, for each order, calls {@code CoffeeShopFacade.prepareOrder(id, Actor.SYSTEM)} —
 * never a direct status update ({@code CLAUDE.md}). {@code BaristaSupervisor} starts N such loops
 * (N = {@code CoffeeShop.baristaPoolSize()}) as a {@code SmartLifecycle}
 * auto-start (so a resumed, previously paused context restarts them too).
 */
package dev.saberlabs.coffeechat.multithread;
