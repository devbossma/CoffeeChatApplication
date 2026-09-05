/**
 * Pattern: OBSERVER.
 *
 * <p>Notifies interested parties when an order's status changes, using Spring's
 * {@link org.springframework.context.ApplicationEventPublisher} and {@code @EventListener}
 * instead of a hand-rolled observer list.
 */
package dev.saberlabs.coffeechat.observer;
