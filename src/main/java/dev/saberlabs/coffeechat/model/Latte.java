package dev.saberlabs.coffeechat.model;

import java.math.BigDecimal;

/**
 * Concrete Factory Method product: a plain Latte ($4.00), carried over from
 * {@code MyDesignPattern}.
 */
public final class Latte implements Coffee {

    private static final BigDecimal COST = new BigDecimal("4.00");

    @Override
    public String description() {
        return CoffeeType.LATTE.displayName();
    }

    @Override
    public BigDecimal cost() {
        return COST;
    }

    @Override
    public CoffeeType type() {
        return CoffeeType.LATTE;
    }
}
