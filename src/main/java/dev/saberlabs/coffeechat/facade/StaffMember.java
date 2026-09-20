package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.model.Role;

/** A created staff account, as the facade reports it (no entity leaves the service layer). */
public record StaffMember(Long id, String name, Role role) {
}
