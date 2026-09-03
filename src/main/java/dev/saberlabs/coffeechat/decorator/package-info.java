/**
 * Pattern: DECORATOR.
 *
 * <p>Wraps a base {@code Coffee} with extras (milk, sugar, whipped cream, ...). Deliberately
 * plain OOP, not Spring-managed beans: a decorated coffee is a value built once per order, not
 * something that belongs in the container. See {@code PRD.md} section 7.2, row 5.
 */
package dev.saberlabs.coffeechat.decorator;
