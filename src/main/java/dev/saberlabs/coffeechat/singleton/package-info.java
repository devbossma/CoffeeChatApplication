/**
 * Pattern: SINGLETON.
 *
 * <p>The {@code CoffeeShop} bean managing orders globally. A plain {@code @Component} is
 * enough — Spring's default bean scope already is a singleton, so there's no hand-written
 * double-checked locking here like the pre-Spring version needed.
 * See {@code PRD.md} section 7.2, row 1.
 */
package dev.saberlabs.coffeechat.singleton;
