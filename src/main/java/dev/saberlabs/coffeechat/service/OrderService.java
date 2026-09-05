package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.model.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store of orders.
 *
 * <p><strong>Part 01 stand-in for the Part 03 Spring Data JPA {@code OrderRepository}.</strong>
 * PRD &sect;12 milestone 2 is explicitly "no persistence yet"; this class holds the same role the
 * JPA repository will (the single owner of order rows, id generation) so that {@code OrderInvoker}
 * and {@code CoffeeShopFacade} can be written against a stable seam now and only the storage
 * swapped later. It is deliberately <em>not</em> on the {@code CoffeeShop} singleton &mdash; that
 * bean owns shop-open/menu/config state only ({@code CLAUDE.md}).
 */
@Service
public class OrderService {

    private final ConcurrentMap<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * Stores the order, assigning a generated id if it does not have one yet.
     *
     * @return the same order instance, now guaranteed to have an id
     */
    public Order save(Order order) {
        if (order.id() == null) {
            order.assignId(sequence.incrementAndGet());
        }
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> findByCustomer(Long customerId) {
        return orders.values().stream()
                .filter(o -> o.customer().id() != null && o.customer().id().equals(customerId))
                .toList();
    }

    /** Test/reset aid &mdash; the JPA repository will not need this. */
    public void clear() {
        orders.clear();
        sequence.set(0);
    }
}
