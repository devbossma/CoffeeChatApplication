package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.StaffMember;
import dev.saberlabs.coffeechat.model.Role;

/** A created staff account. The id is what the person then sends as {@code X-User-Id}. */
public record StaffResponse(Long id, String name, Role role) {

    public static StaffResponse from(StaffMember member) {
        return new StaffResponse(member.id(), member.name(), member.role());
    }
}
