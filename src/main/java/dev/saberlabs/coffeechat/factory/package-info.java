/**
 * Pattern: FACTORY METHOD.
 *
 * <p>{@code CoffeeFactory} (a Spring {@code @Service}) hands out each base coffee type
 * (Espresso, Cappuccino, Latte, ...) through a {@code Map<CoffeeType, Supplier<Coffee>>}, so
 * callers ask for a coffee by type instead of constructing a concrete class and there is no
 * {@code switch} to fall through. Each call returns a fresh instance &mdash; a coffee is a
 * per-order value the Decorator then wraps, never a shared bean.
 */
package dev.saberlabs.coffeechat.factory;
