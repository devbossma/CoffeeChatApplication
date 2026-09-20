package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The one enforced {@code Role} boundary (CLAUDE.md, resolved decision 3): a plain service-layer check,
 * no Spring Security. Called from inside the lifecycle command's transaction so the check and the
 * change are atomic and a rejected action leaves no trace.
 *
 * <p>{@code @Transactional(readOnly = true)} (REQUIRED) joins the command's transaction when there is
 * one and otherwise opens its own, so the facade can also call it up front (for undo).
 */
@Service
public class StaffAccess {

    /** Roles allowed to make an order status transition. */
    public static final Set<Role> STAFF = Set.copyOf(EnumSet.of(Role.BARISTA, Role.MANAGER));

    private final UserRepository users;

    public StaffAccess(@NotNull UserRepository users) {
        this.users = Objects.requireNonNull(users, "users cannot be null");
    }

    /**
     * Checks that {@code actor} may perform an action reserved for {@code allowed} roles.
     *
     * @return the id to record as {@code order_status_history.changed_by}: the user's id only when
     *         their role is BARISTA; {@code null} for {@link Actor#SYSTEM} and for a MANAGER
     * @throws UnknownActorException   if the actor is a user id that does not exist
     * @throws RoleNotAllowedException if the user's role is not in {@code allowed}
     */
    @Transactional(readOnly = true)
    public Long authorize(@NotNull Actor actor, @NotNull Set<Role> allowed) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(allowed, "allowed cannot be null");
        if (actor.isSystem()) {
            return null;
        }
        UserEntity user = find(actor.userId());
        if (!allowed.contains(user.role())) {
            throw new RoleNotAllowedException(user.id(), user.role(), "perform this action (requires one of " + allowed + ")");
        }
        return user.role() == Role.BARISTA ? user.id() : null;
    }

    /**
     * Who may pay an order: staff (collecting at the counter), the order's own CUSTOMER, or
     * {@link Actor#SYSTEM}. A different customer paying someone else's order is rejected.
     */
    @Transactional(readOnly = true)
    public void authorizePayer(@NotNull Actor actor, @NotNull OrderEntity order) {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(order, "order cannot be null");
        if (actor.isSystem()) {
            return;
        }
        UserEntity user = find(actor.userId());
        boolean staff = STAFF.contains(user.role());
        boolean owner = user.role() == Role.CUSTOMER && user.id().equals(order.customerId());
        if (!staff && !owner) {
            throw new RoleNotAllowedException(user.id(), user.role(), "pay order " + order.id());
        }
    }

    private UserEntity find(Long userId) {
        return users.findById(userId).orElseThrow(() -> UnknownActorException.noSuchUser(userId));
    }
}
