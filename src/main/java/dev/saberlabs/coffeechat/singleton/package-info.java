/**
 * Pattern: SINGLETON.
 *
 * <p>The {@code CoffeeShop} bean managing orders globally. A plain {@code @Component} is
 * enough — Spring's default bean scope already is a singleton, so there's no hand-written
 * double-checked locking here like the pre-Spring version needed.
 */
package dev.saberlabs.coffeechat.singleton;
