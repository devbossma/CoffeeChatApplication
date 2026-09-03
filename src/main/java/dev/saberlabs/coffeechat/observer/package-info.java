/**
 * Pattern: OBSERVER.
 *
 * <p>Notifies interested parties when an order's status changes, using Spring's
 * {@link org.springframework.context.ApplicationEventPublisher} and {@code @EventListener}
 * instead of a hand-rolled observer list. See {@code PRD.md} section 7.2, row 3.
 */
package dev.saberlabs.coffeechat.observer;
