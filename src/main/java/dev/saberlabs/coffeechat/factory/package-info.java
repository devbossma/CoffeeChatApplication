/**
 * Pattern: FACTORY METHOD.
 *
 * <p>Builds each coffee type (Espresso, Cappuccino, Latte, ...) behind one
 * {@code CoffeeFactory}, a Spring {@code @Service}, so callers ask for a coffee by type
 * instead of constructing a concrete class directly.
 */
package dev.saberlabs.coffeechat.factory;
