package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;

import java.math.BigDecimal;

/**
 * Pattern 3: DECORATOR &mdash; adds sugar: {@code + $0.25}, {@code " + Sugar"}.
 */
public final class SugarDecorator extends CoffeeDecorator {

    static final BigDecimal SURCHARGE = new BigDecimal("0.25");

    public SugarDecorator(Coffee inner) {
        super(inner);
    }

    @Override
    public String description() {
        return inner.description() + " + Sugar";
    }

    @Override
    public BigDecimal cost() {
        return inner.cost().add(SURCHARGE);
    }
}
