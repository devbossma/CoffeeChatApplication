package dev.saberlabs.coffeechat.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/staff}. {@link StaffRole} has no CUSTOMER value, so asking for one is an unreadable
 * body (400) rather than something the service has to refuse.
 */
public record CreateStaffRequest(@NotBlank @Size(max = 255) String name, @NotNull StaffRole role) {

    /** The roles that can be created here. */
    public enum StaffRole {
        BARISTA, MANAGER
    }
}
