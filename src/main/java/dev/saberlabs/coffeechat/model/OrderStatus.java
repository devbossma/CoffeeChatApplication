package dev.saberlabs.coffeechat.model;

import java.util.Set;

/**
 * The order lifecycle, carried over from {@code MyDesignPattern} (PRD &sect;6):
 * {@code PLACED -> PREPARING -> READY -> FULFILLED}, plus {@code CANCELLED} as an alternate
 * terminal state.
 *
 * <p>Unlike the reference project's permissive {@code setStatus}, this enum also knows which
 * transitions are legal ({@link #canTransitionTo(OrderStatus)}) so an illegal move (e.g. paying
 * a cancelled order, re-fulfilling a fulfilled one) fails loudly instead of silently corrupting
 * state.
 */
public enum OrderStatus {
    PLACED,
    PREPARING,
    READY,
    FULFILLED,
    CANCELLED;

    /**
     * @return {@code true} if an order currently in this status may move to {@code target}.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return NEXT.getOrDefault(this, Set.of()).contains(target);
    }

    private static final java.util.Map<OrderStatus, Set<OrderStatus>> NEXT = java.util.Map.of(
            PLACED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY, CANCELLED),
            READY, Set.of(FULFILLED, CANCELLED),
            FULFILLED, Set.of(),
            CANCELLED, Set.of()
    );
}
