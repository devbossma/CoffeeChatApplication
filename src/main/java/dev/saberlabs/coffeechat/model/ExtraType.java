package dev.saberlabs.coffeechat.model;

/**
 * The extras a customer can add to a base coffee (Decorator pattern).
 *
 * <p>Carried over from {@code MyDesignPattern} (PRD &sect;6): Milk, Sugar, Whipped Cream. The
 * per-extra surcharge is deliberately <em>not</em> stored here &mdash; each
 * {@code dev.saberlabs.coffeechat.decorator.CoffeeDecorator} subclass owns its own cost
 * increment, the same way the reference project does, so the Decorator classes stay the single
 * source of truth for what an extra adds.
 */
public enum ExtraType {
    MILK,
    SUGAR,
    WHIPPED_CREAM
}
