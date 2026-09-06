/**
 * Pattern: SINGLETON.
 *
 * <p>{@code CoffeeShop} is a plain {@code @Component} &mdash; Spring's default scope already is a
 * singleton, so there is no hand-written double-checked locking. It owns only shop-wide
 * operational state that is inherently singular: whether the shop is accepting orders, the
 * active menu of {@code CoffeeType}s, and the barista pool size. It holds no order data and no
 * id counters &mdash; those belong to {@code OrderService} / the Part 03 repository, not here
 * ({@code CLAUDE.md}).
 */
package dev.saberlabs.coffeechat.singleton;
