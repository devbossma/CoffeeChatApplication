package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.OrderNotFoundException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.repository.OrderRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistence-facing order operations, over {@link OrderRepository} &mdash; the only holder of
 * order state is the database.
 *
 * <p>Two kinds of method: those that hand out an <em>entity</em> ({@link #create}, {@link #require},
 * {@link #flush}) are {@code MANDATORY}-transactional, because a managed entity is only meaningful
 * inside the command transaction that will flush it (and its {@code @Version} check); those that
 * hand out a {@link Order} snapshot ({@link #findById}) open their own read-only transaction.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderMapper mapper;

    public OrderService(@NotNull OrderRepository orders, @NotNull OrderMapper mapper) {
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    /** Inserts a new order at {@code PLACED}; the caller has already derived tier and price. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEntity create(@NotNull UserEntity customer,
                              @NotNull CoffeeType type,
                              @NotNull List<ExtraType> extras,
                              @NotNull LoyaltyTier appliedTier,
                              @NotNull PriceBreakdown price) {
        Instant now = Instant.now();
        return orders.saveAndFlush(new OrderEntity(customer, type, extras, OrderStatus.PLACED, appliedTier, price, now, now));
    }

    /**
     * Loads the managed entity a command is about to mutate.
     *
     * @throws OrderNotFoundException if no order has that id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEntity require(@NotNull Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Loads the order with a row lock, for payment: serialises concurrent payers of one order before
     * the gateway is called.
     *
     * @throws OrderNotFoundException if no order has that id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEntity requireForPayment(@NotNull Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return orders.findForUpdateById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Forces pending changes to the database now, so the {@code @Version} check fires here. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void flush() {
        orders.flush();
    }

    @Transactional(readOnly = true)
    public Optional<Order> findById(@NotNull Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");
        return orders.findById(orderId).map(mapper::toSnapshot);
    }

    /** The ids of orders still awaiting preparation (PLACED, or PREPARING left by an interrupted run), oldest first. */
    @Transactional(readOnly = true)
    public List<Long> findUnfinishedOrderIds() {
        return orders.findIdsByStatusIn(List.of(OrderStatus.PLACED, OrderStatus.PREPARING));
    }

    @Transactional(readOnly = true)
    public List<Order> findByCustomer(@NotNull Long customerId) {
        Objects.requireNonNull(customerId, "customerId cannot be null");
        return orders.findByCustomerId(customerId).stream().map(mapper::toSnapshot).toList();
    }
}
