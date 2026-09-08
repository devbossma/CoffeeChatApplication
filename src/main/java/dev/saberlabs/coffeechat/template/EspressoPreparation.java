package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;

/**
 * Pattern 10: TEMPLATE METHOD &mdash; Espresso: 95°C, 25s, a single-shot pull, no condiments.
 */
public final class EspressoPreparation extends CoffeePreparationTemplate {

    public EspressoPreparation() {
        super(CoffeeType.ESPRESSO);
    }

    @Override
    protected int targetTemperatureCelsius() {
        return 95;
    }

    @Override
    protected int boilDurationSeconds() {
        return 25;
    }

    @Override
    protected void brew() {
        log("Pulling a single espresso shot");
    }

    @Override
    protected void addCondiments() {
        log("No condiments for espresso");
    }
}
