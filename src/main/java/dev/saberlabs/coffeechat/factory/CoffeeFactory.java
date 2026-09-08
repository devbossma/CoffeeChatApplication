package dev.saberlabs.coffeechat.factory;

import dev.saberlabs.coffeechat.model.Cappuccino;
import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.Espresso;
import dev.saberlabs.coffeechat.model.Latte;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pattern 2: FACTORY METHOD.
 *
 * <p>{@code MyDesignPattern} used one {@code CoffeeCreator} subclass per type
 * ({@code EspressoCreator}, ...). The assignment's own wording is "a single {@code CoffeeFactory}
 * {@code @Service}", so that is what this is &mdash; but the branching is a
 * {@code Map<CoffeeType, Supplier<Coffee>>} rather than a {@code switch}, so adding a type is a
 * one-line map entry and there is no conditional to fall through.
 *
 * <p>Each {@link Supplier} returns a <em>fresh</em> {@link Coffee}: a coffee is a per-order value
 * that then gets wrapped by decorators, so it must never be a shared bean.
 */
@Service
public class CoffeeFactory {

    private final Map<CoffeeType, Supplier<Coffee>> creators;

    public CoffeeFactory() {
        this.creators = new EnumMap<>(CoffeeType.class);
        creators.put(CoffeeType.ESPRESSO, Espresso::new);
        creators.put(CoffeeType.CAPPUCCINO, Cappuccino::new);
        creators.put(CoffeeType.LATTE, Latte::new);
    }

    /**
     * Creates a new base coffee of the given type.
     *
     * @param type which coffee to make
     * @return a fresh, undecorated {@link Coffee}
     * @throws IllegalArgumentException if {@code type} is {@code null} or has no registered creator
     */
    public Coffee create(CoffeeType type) {
        if (type == null) {
            throw new IllegalArgumentException("Coffee type cannot be null");
        }
        Supplier<Coffee> creator = creators.get(type);
        if (creator == null) {
            throw new IllegalArgumentException("No creator registered for coffee type: " + type);
        }
        return creator.get();
    }
}
