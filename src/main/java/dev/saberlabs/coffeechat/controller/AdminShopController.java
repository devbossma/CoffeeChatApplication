package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two endpoints that mutate the {@link CoffeeShop} singleton: open / close the shop for
 * new orders ({@code CLAUDE.md}). {@code GET} exposes the current state.
 */
@RestController
@RequestMapping("/api/admin/shop")
public class AdminShopController {

    private final CoffeeShop coffeeShop;

    public AdminShopController(CoffeeShop coffeeShop) {
        this.coffeeShop = coffeeShop;
    }

    @GetMapping
    public ShopStateResponse state() {
        return currentState();
    }

    @PostMapping("/open")
    public ShopStateResponse open() {
        coffeeShop.open();
        return currentState();
    }

    @PostMapping("/close")
    public ShopStateResponse close() {
        coffeeShop.close();
        return currentState();
    }

    private ShopStateResponse currentState() {
        return new ShopStateResponse(coffeeShop.isOpen(), coffeeShop.activeMenu());
    }
}
