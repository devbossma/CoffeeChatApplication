package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two endpoints that mutate the {@link CoffeeShop} singleton: open / close the shop for
 * new orders ({@code CLAUDE.md}), through the facade, which requires a MANAGER caller
 * ({@code X-User-Id}). {@code GET} exposes the current state and needs no identity.
 */
@RestController
@RequestMapping("/api/admin/shop")
public class AdminShopController {

    private final CoffeeShop coffeeShop;
    private final CoffeeShopFacade facade;

    public AdminShopController(CoffeeShop coffeeShop, CoffeeShopFacade facade) {
        this.coffeeShop = coffeeShop;
        this.facade = facade;
    }

    @GetMapping
    public ShopStateResponse state() {
        return currentState();
    }

    @PostMapping("/open")
    public ShopStateResponse open(Actor actor) {
        facade.openShop(actor);
        return currentState();
    }

    @PostMapping("/close")
    public ShopStateResponse close(Actor actor) {
        facade.closeShop(actor);
        return currentState();
    }

    private ShopStateResponse currentState() {
        return new ShopStateResponse(coffeeShop.isOpen(), coffeeShop.activeMenu());
    }
}
