package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body of {@code POST /api/orders}. {@code extras} is optional; a missing/blank list means "no
 * extras".
 */
public record PlaceOrderHttpRequest(@NotNull Long customerId,
                                    @NotNull CoffeeType type,
                                    List<ExtraType> extras) {

    public PlaceOrderRequest toFacadeRequest() {
        return new PlaceOrderRequest(customerId, type, extras == null ? List.of() : extras);
    }
}
