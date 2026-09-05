package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;

import java.math.BigDecimal;

/**
 * Pattern 3: DECORATOR &mdash; adds milk: {@code + $0.50}, {@code " + Milk"}.
 */
public final class MilkDecorator extends CoffeeDecorator {

    static final BigDecimal SURCHARGE = new BigDecimal("0.50");

    public MilkDecorator(Coffee inner) {
        super(inner);
    }

    @Override
    public String description() {
        return inner.description() + " + Milk";
    }

    @Override
    public BigDecimal cost() {
        return inner.cost().add(SURCHARGE);
    }
}
