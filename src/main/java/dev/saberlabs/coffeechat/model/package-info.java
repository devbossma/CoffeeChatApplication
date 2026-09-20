/**
 * Plain domain value types: the immutable {@code Order} snapshot, {@code Coffee}, {@code PriceBreakdown}
 * and the enums describing lifecycle and roles (order status, loyalty tier, role, session status,
 * message type). Nothing here is mutable persisted state: that lives only in the {@code entity}
 * package ({@code OrderEntity}, {@code UserEntity}, ...), and {@code Order} is a read-only snapshot
 * built from an {@code OrderEntity} inside a transaction.
 */
package dev.saberlabs.coffeechat.model;
