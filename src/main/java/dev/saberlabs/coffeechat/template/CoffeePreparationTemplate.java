package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pattern 10: TEMPLATE METHOD.
 *
 * <p>{@link #prepare()} is {@code final}: the sequence &mdash; boil water, brew, pour, add
 * condiments, serve &mdash; is fixed and no subclass can reorder or skip a step. Only the steps
 * that vary by coffee type ({@link #brew()}, {@link #addCondiments()}, {@link #targetTemperatureCelsius()},
 * {@link #boilDurationSeconds()}) are left abstract.
 *
 * <p>Unchanged from {@code MyDesignPattern}: Spring has no special idiom for Template Method, so
 * these stay plain classes. Each instance accumulates a step-by-step {@link #log()} and is
 * therefore single-use &mdash; {@code CoffeePreparationResolver} hands out a fresh one per order.
 */
public abstract class CoffeePreparationTemplate {

    private final CoffeeType coffeeType;
    private final List<String> log = new ArrayList<>();

    protected CoffeePreparationTemplate(CoffeeType coffeeType) {
        this.coffeeType = coffeeType;
    }

    /** The fixed preparation algorithm. Cannot be overridden. */
    public final void prepare() {
        boilWater();
        brew();
        pourIntoCup();
        addCondiments();
        log(coffeeType.displayName() + " is ready");
    }

    private void boilWater() {
        log("Boiling water to %d°C for %d seconds"
                .formatted(targetTemperatureCelsius(), boilDurationSeconds()));
    }

    private void pourIntoCup() {
        log("Pouring into cup");
    }

    protected abstract int targetTemperatureCelsius();

    protected abstract int boilDurationSeconds();

    protected abstract void brew();

    protected abstract void addCondiments();

    protected final void log(String step) {
        log.add(step);
    }

    /** The steps performed so far, in order. Empty until {@link #prepare()} has run. */
    public List<String> log() {
        return Collections.unmodifiableList(log);
    }

    public CoffeeType coffeeType() {
        return coffeeType;
    }
}
