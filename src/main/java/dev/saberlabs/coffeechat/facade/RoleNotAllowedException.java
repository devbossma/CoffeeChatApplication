package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.model.Role;

/**
 * A known user whose role does not permit the requested action. Mapped to 403.
 */
public class RoleNotAllowedException extends RuntimeException {

    private final Long userId;
    private final Role role;

    public RoleNotAllowedException(Long userId, Role role, String action) {
        super("User " + userId + " (" + role + ") is not allowed to " + action);
        this.userId = userId;
        this.role = role;
    }

    public Long userId() {
        return userId;
    }

    public Role role() {
        return role;
    }
}
