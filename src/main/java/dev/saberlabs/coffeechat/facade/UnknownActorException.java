package dev.saberlabs.coffeechat.facade;

/**
 * The caller could not be identified: no actor was supplied, or the claimed user id does not exist.
 * Mapped to 401. Distinct from {@link RoleNotAllowedException} (a known user who may not do this, 403).
 */
public class UnknownActorException extends RuntimeException {

    public UnknownActorException(String message) {
        super(message);
    }

    public static UnknownActorException noSuchUser(Long userId) {
        return new UnknownActorException("No user with id " + userId);
    }
}
