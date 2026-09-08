package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.model.Order;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * REST surface for orders (PRD &sect;9.4). Every call goes through {@link CoffeeShopFacade} &mdash;
 * the controller never touches {@code OrderService} / a {@code Command} directly ({@code CLAUDE.md}).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CoffeeShopFacade facade;

    public OrderController(CoffeeShopFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderHttpRequest request,
                                               UriComponentsBuilder uriBuilder) {
        Order order = facade.placeOrder(request.toFacadeRequest());
        return created(order, uriBuilder);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(facade.getOrder(id));
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<OrderResponse> reorder(@PathVariable Long id, UriComponentsBuilder uriBuilder) {
        Order clone = facade.reorder(id);
        return created(clone, uriBuilder);
    }

    private static ResponseEntity<OrderResponse> created(Order order, UriComponentsBuilder uriBuilder) {
        URI location = uriBuilder.path("/api/orders/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }
}
