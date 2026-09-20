package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import dev.saberlabs.coffeechat.repository.OrderRepository;
import dev.saberlabs.coffeechat.repository.OrderStatusHistoryRepository;
import dev.saberlabs.coffeechat.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Writes the durable audit row for every {@link OrderStatusChangedEvent}, <em>in the same
 * transaction</em> as the status change that raised it.
 *
 * <p>Deliberately a plain {@code @EventListener} (synchronous, in the publisher's thread and
 * transaction) with {@code MANDATORY} propagation, not a {@code @TransactionalEventListener}: the
 * audit must commit or roll back with the state it describes. {@code MANDATORY} makes a call with no
 * transaction fail loudly instead of quietly writing an audit row in a transaction of its own that
 * could outlive a rolled-back status change. If this listener throws, the whole command rolls back.
 *
 * <p>{@code changed_by} comes from the event's {@code actorUserId}, which is {@code null} in Step 3
 * (every transition is a system transition). Step 4 supplies a BARISTA actor and adds the
 * application-layer role check; a plain foreign key cannot enforce that.
 */
@Component
public class OrderStatusHistoryListener {

    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderStatusHistoryRepository history;

    public OrderStatusHistoryListener(@NotNull OrderRepository orders,
                                      @NotNull UserRepository users,
                                      @NotNull OrderStatusHistoryRepository history) {
        this.orders = Objects.requireNonNull(orders, "orders cannot be null");
        this.users = Objects.requireNonNull(users, "users cannot be null");
        this.history = Objects.requireNonNull(history, "history cannot be null");
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        history.save(new OrderStatusHistoryEntity(
                orders.getReferenceById(event.orderId()),
                event.from(),
                event.to(),
                event.at(),
                event.actorUserId() == null ? null : users.getReferenceById(event.actorUserId())));
    }
}
