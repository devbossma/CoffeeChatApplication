/**
 * Pattern: COMMAND.
 *
 * <p>The order lifecycle (place / prepare / pay / fulfill / cancel) as discrete
 * {@code OrderCommand} objects run through {@code OrderInvoker} (a {@code @Service}), which keeps
 * a capped recent-activity history and an undo stack. {@code CoffeeShopFacade} is the only
 * builder of these commands ({@code CLAUDE.md}). The order-placement command's execution gains
 * {@code @Transactional} in Part 03, once {@code OrderService} is JPA-backed; in Part 01 there is
 * no database to wrap.
 */
package dev.saberlabs.coffeechat.command;
