package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;

/**
 * Pattern 10: TEMPLATE METHOD &mdash; Latte: 93°C, 28s, espresso + a generous pour of steamed
 * milk with light microfoam, finished with vanilla and cocoa.
 */
public final class LattePreparation extends CoffeePreparationTemplate {

    public LattePreparation() {
        super(CoffeeType.LATTE);
    }

    @Override
    protected int targetTemperatureCelsius() {
        return 93;
    }

    @Override
    protected int boilDurationSeconds() {
        return 28;
    }

    @Override
    protected void brew() {
        log("Pulling a double espresso shot");
        log("Steaming milk to a light microfoam");
        log("Pouring steamed milk to fill the cup");
    }

    @Override
    protected void addCondiments() {
        log("Finishing with vanilla and cocoa");
    }
}
