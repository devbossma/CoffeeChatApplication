package dev.saberlabs.coffeechat.model;

/**
 * A person's role in the shop: {@code CUSTOMER}, {@code BARISTA}, or {@code MANAGER}.
 *
 * <p>Backs {@code UserEntity.role} (Part 03) and the resolved auth scope (PRD &sect;11.3 /
 * {@code CLAUDE.md}): only {@code BARISTA}/{@code MANAGER} may drive order-status-transition
 * endpoints, and only {@code BARISTA} users are eligible for {@code BaristaQueue} matching. A
 * plain service-layer check, no Spring Security &mdash; consistent with the "no full auth
 * system" non-goal (PRD &sect;4).
 */
public enum Role {
    CUSTOMER,
    BARISTA,
    MANAGER
}
