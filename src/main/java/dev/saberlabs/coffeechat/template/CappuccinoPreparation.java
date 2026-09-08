package dev.saberlabs.coffeechat.template;

import dev.saberlabs.coffeechat.model.CoffeeType;

/**
 * Pattern 10: TEMPLATE METHOD &mdash; Cappuccino: 90°C, 30s, espresso + steamed milk + a thick
 * foam cap, dusted with cocoa and cinnamon.
 */
public final class CappuccinoPreparation extends CoffeePreparationTemplate {

    public CappuccinoPreparation() {
        super(CoffeeType.CAPPUCCINO);
    }

    @Override
    protected int targetTemperatureCelsius() {
        return 90;
    }

    @Override
    protected int boilDurationSeconds() {
        return 30;
    }

    @Override
    protected void brew() {
        log("Pulling a double espresso shot");
        log("Steaming milk to a stiff microfoam");
        log("Spooning a thick foam cap on top");
    }

    @Override
    protected void addCondiments() {
        log("Dusting with cocoa and cinnamon");
    }
}
