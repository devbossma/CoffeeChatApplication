package dev.saberlabs.coffeechat.model;

import java.math.BigDecimal;

/**
 * Concrete Factory Method product: a plain Espresso ($2.50), carried over from
 * {@code MyDesignPattern}.
 */
public final class Espresso implements Coffee {

    private static final BigDecimal COST = new BigDecimal("2.50");

    @Override
    public String description() {
        return CoffeeType.ESPRESSO.displayName();
    }

    @Override
    public BigDecimal cost() {
        return COST;
    }

    @Override
    public CoffeeType type() {
        return CoffeeType.ESPRESSO;
    }
}
