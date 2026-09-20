package dev.saberlabs.coffeechat.facade;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * Who is performing a lifecycle action: either a person (a user id) or the application itself.
 *
 * <p>There is no authentication in this project (PRD section 4); an {@code Actor.user(id)} is a
 * <em>claimed</em> identity, and the facade's job is to check that the claimed user exists and has a
 * role that permits the action. {@link #SYSTEM} is for trusted in-process automation only (the async
 * barista loops, {@code processOrder}, tests) and skips the role check; nothing in the {@code controller}
 * package may reference it, which a guard test enforces, so an endpoint can never accidentally act as
 * the system.
 */
public record Actor(Long userId) {

    /** Trusted in-process automation: not a person, so no role check and no {@code changed_by}. */
    public static final Actor SYSTEM = new Actor(null);

    public static Actor user(@NotNull Long userId) {
        return new Actor(Objects.requireNonNull(userId, "userId cannot be null"));
    }

    public boolean isSystem() {
        return userId == null;
    }
}
