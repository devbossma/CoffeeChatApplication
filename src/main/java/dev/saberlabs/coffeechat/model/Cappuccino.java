package dev.saberlabs.coffeechat.model;

import java.math.BigDecimal;

/**
 * Concrete Factory Method product: a plain Cappuccino ($3.50), carried over from
 * {@code MyDesignPattern}.
 */
public final class Cappuccino implements Coffee {

    private static final BigDecimal COST = new BigDecimal("3.50");

    @Override
    public String description() {
        return CoffeeType.CAPPUCCINO.displayName();
    }

    @Override
    public BigDecimal cost() {
        return COST;
    }

    @Override
    public CoffeeType type() {
        return CoffeeType.CAPPUCCINO;
    }
}
