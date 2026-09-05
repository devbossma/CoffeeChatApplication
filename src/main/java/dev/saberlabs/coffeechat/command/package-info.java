/**
 * Pattern: COMMAND.
 *
 * <p>The order lifecycle (place / prepare / pay / fulfill) as discrete commands run through
 * an invoker that keeps a history, with order placement wrapped in {@code @Transactional}
 * now that it touches a real database.
 */
package dev.saberlabs.coffeechat.command;
