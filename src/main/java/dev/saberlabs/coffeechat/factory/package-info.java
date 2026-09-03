/**
 * Pattern: FACTORY METHOD.
 *
 * <p>Builds each coffee type (Espresso, Cappuccino, Latte, ...) behind one
 * {@code CoffeeFactory}, a Spring {@code @Service}, so callers ask for a coffee by type
 * instead of constructing a concrete class directly. See {@code PRD.md} section 7.2, row 2.
 */
package dev.saberlabs.coffeechat.factory;
