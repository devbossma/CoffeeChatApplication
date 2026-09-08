package dev.saberlabs.coffeechat.model;

import java.math.BigDecimal;

/**
 * A coffee &mdash; either a plain base coffee (Factory Method product) or one wrapped in extras
 * (Decorator). It is the Component of the Decorator pattern and the Product of the Factory
 * Method pattern.
 *
 * <p>Departure from {@code MyDesignPattern}: this interface no longer carries
 * {@code getPreparation()}. Wiring a coffee to its Template Method preparation is done by
 * {@code CoffeePreparationResolver}, keyed on {@link #type()}, so the domain model does not have
 * to depend on the {@code template} package. Prices are {@link BigDecimal}, not {@code double},
 * so money never picks up floating-point error.
 */
public interface Coffee {

    /** Human-readable description, e.g. {@code "Latte + Milk + Whipped Cream"}. */
    String description();

    /** Total cost of this coffee including every extra wrapped around it. */
    BigDecimal cost();

    /** The base coffee type underneath any decorators &mdash; unchanged by wrapping. */
    CoffeeType type();
}
