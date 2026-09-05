package dev.saberlabs.coffeechat.decorator;

import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.CoffeeType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pattern 3: DECORATOR &mdash; abstract decorator.
 *
 * <p>Wraps any {@link Coffee} and, by default, forwards every call to it. A concrete decorator
 * overrides {@link #cost()} / {@link #description()} to add its own increment on top. The base
 * type ({@link #type()}) is always whatever the innermost coffee reports &mdash; wrapping never
 * changes it.
 *
 * <p>Deliberately plain OOP: a decorated coffee is a value built once per order, so none of these
 * classes are Spring beans (see {@code package-info}).
 */
public abstract class CoffeeDecorator implements Coffee {

    protected final Coffee inner;

    protected CoffeeDecorator(Coffee inner) {
        this.inner = Objects.requireNonNull(inner, "wrapped coffee cannot be null");
    }

    @Override
    public String description() {
        return inner.description();
    }

    @Override
    public BigDecimal cost() {
        return inner.cost();
    }

    @Override
    public CoffeeType type() {
        return inner.type();
    }
}
