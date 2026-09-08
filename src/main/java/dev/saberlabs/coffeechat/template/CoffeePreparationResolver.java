package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Resolves the {@link CoffeePreparationTemplate} for a coffee type.
 *
 * <p>Replaces {@code MyDesignPattern}'s {@code Coffee.getPreparation()}: keeping the wiring here,
 * keyed on {@link CoffeeType}, means the domain model does not depend on the {@code template}
 * package. Templates are single-use (each accumulates its own step log), so the map holds
 * {@link Supplier}s and every call returns a fresh instance &mdash; the same
 * resolver-over-an-EnumMap shape used for pricing strategies and payment gateways.
 */
@Component
public class CoffeePreparationResolver {

    private final Map<CoffeeType, Supplier<CoffeePreparationTemplate>> templates;

    public CoffeePreparationResolver() {
        this.templates = new EnumMap<>(CoffeeType.class);
        templates.put(CoffeeType.ESPRESSO, EspressoPreparation::new);
        templates.put(CoffeeType.CAPPUCCINO, CappuccinoPreparation::new);
        templates.put(CoffeeType.LATTE, LattePreparation::new);
    }

    /**
     * @param type the coffee type to prepare
     * @return a fresh, un-run preparation template for that type
     * @throws IllegalArgumentException if {@code type} is null or has no registered template
     */
    public CoffeePreparationTemplate forType(CoffeeType type) {
        if (type == null) {
            throw new IllegalArgumentException("Coffee type cannot be null");
        }
        Supplier<CoffeePreparationTemplate> supplier = templates.get(type);
        if (supplier == null) {
            throw new IllegalArgumentException("No preparation template registered for coffee type: " + type);
        }
        return supplier.get();
    }
}
