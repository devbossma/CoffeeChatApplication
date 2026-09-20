package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
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
 * <p>{@code changed_by} comes from the event's {@code actorUserId}: NULL for a system or
 * manager-driven transition, a BARISTA's id for a barista-driven one. This listener verifies that a
 * non-null id really belongs to a BARISTA (one extra query per such event, in the same transaction).
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
                actorFor(event)));
    }

    /**
     * The one place {@code changed_by} is written, so the one place the rule "only a BARISTA may be
     * recorded" is enforced for real: a plain foreign key cannot check the referenced user's role. Runs
     * in the same transaction as the status change, so a violation rolls the whole change back.
     */
    private UserEntity actorFor(OrderStatusChangedEvent event) {
        Long actorId = event.actorUserId();
        if (actorId == null) {
            return null;
        }
        UserEntity actor = users.findById(actorId).orElseThrow(() -> new IllegalStateException(
                "Cannot record status change of order " + event.orderId() + ": changed_by user " + actorId + " does not exist"));
        if (actor.role() != Role.BARISTA) {
            throw new IllegalStateException("Cannot record status change of order " + event.orderId()
                    + ": changed_by user " + actorId + " has role " + actor.role() + ", only a BARISTA may be recorded");
        }
        return actor;
    }
}
