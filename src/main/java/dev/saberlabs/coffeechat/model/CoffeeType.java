package dev.saberlabs.coffeechat.model;

/**
 * The coffee types the shop can serve.
 *
 * <p>Carried over verbatim from {@code MyDesignPattern} (PRD &sect;6): Espresso, Cappuccino, Latte.
 * This enum is the key type for the Factory Method lookup ({@code CoffeeFactory}), the Template
 * Method resolver, and {@code CoffeeShop}'s active menu &mdash; the concrete price of each type
 * lives on its {@link Coffee} implementation, not here, so the Factory Method still has real
 * concrete products to instantiate.
 */
public enum CoffeeType {
    ESPRESSO("Espresso"),
    CAPPUCCINO("Cappuccino"),
    LATTE("Latte");

    private final String displayName;

    CoffeeType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
