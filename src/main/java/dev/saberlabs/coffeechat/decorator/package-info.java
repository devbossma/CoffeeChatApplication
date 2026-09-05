/**
 * Pattern: DECORATOR.
 *
 * <p>Wraps a base {@code Coffee} with extras (milk, sugar, whipped cream, ...). Deliberately
 * plain OOP, not Spring-managed beans: a decorated coffee is a value built once per order, not
 * something that belongs in the container.
 */
package dev.saberlabs.coffeechat.decorator;
