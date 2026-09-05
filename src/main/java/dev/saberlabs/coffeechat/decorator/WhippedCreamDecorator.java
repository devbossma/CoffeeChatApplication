package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;

import java.math.BigDecimal;

/**
 * Pattern 3: DECORATOR &mdash; adds whipped cream: {@code + $0.75}, {@code " + Whipped Cream"}.
 */
public final class WhippedCreamDecorator extends CoffeeDecorator {

    static final BigDecimal SURCHARGE = new BigDecimal("0.75");

    public WhippedCreamDecorator(Coffee inner) {
        super(inner);
    }

    @Override
    public String description() {
        return inner.description() + " + Whipped Cream";
    }

    @Override
    public BigDecimal cost() {
        return inner.cost().add(SURCHARGE);
    }
}
