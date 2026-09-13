package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.observer.OrderStatusChangedEvent;
import dev.saberlabs.coffeechat.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enqueues a newly-placed order onto {@link OrderQueue} for the Barista pool to pick up.
 *
 * <p>A second, independent {@code @EventListener} on {@link OrderStatusChangedEvent}, alongside
 * {@code dev.saberlabs.coffeechat.observer.OrderNotificationListener} &mdash; not the same
 * listener doing both jobs. The one-listener rule in {@code CLAUDE.md}'s source-of-truth table is
 * scoped to <em>notifications</em>; queue dispatch is an unrelated concern reacting to the same
 * event, which is exactly what an event bus is for. {@code CoffeeShopFacade} never gets a direct
 * dependency on {@code OrderQueue}.
 *
 * <p>By the time this listener runs, the order is already saved in {@code OrderService}
 * ({@code AbstractOrderCommand.transition()} calls {@code orders.save(order)} before publishing,
 * on the same thread, and Spring's default {@code @EventListener} is synchronous) &mdash; so the
 * lookup below cannot race the placement.
 */
@Component
public class OrderQueueDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderQueueDispatcher.class);

    private final OrderQueue orderQueue;
    private final OrderService orders;

    public OrderQueueDispatcher(OrderQueue orderQueue, OrderService orders) {
        this.orderQueue = orderQueue;
        this.orders = orders;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.to() != OrderStatus.PLACED) {
            return;
        }
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("PLACED event for order {} but it is not in OrderService — not enqueued", event.orderId());
            return;
        }
        orderQueue.enqueue(order);
    }
}
