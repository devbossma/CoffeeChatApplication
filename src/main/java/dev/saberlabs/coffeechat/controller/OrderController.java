package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.model.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * the controller never touches {@code OrderService} / a {@code Command} directly ({@code CLAUDE.md}) &mdash;
 * and every call names its caller ({@code X-User-Id}, resolved to an {@link Actor} by the web layer); the
 * facade enforces who may do what.
 *
 * <p>There is deliberately no undo endpoint: the undo stack is global across all users, so exposing it would let
 * any staff member cancel the last order placed by anyone. {@code CoffeeShopFacade.undoLastAction} stays for
 * internal use.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final CoffeeShopFacade facade;

    public OrderController(CoffeeShopFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderHttpRequest request, Actor actor,
                                               UriComponentsBuilder uriBuilder) {
        return created(facade.placeOrder(request.toFacadeRequest(), actor), uriBuilder);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Actor actor) {
        return OrderResponse.from(facade.getOrder(id, actor));
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<OrderResponse> reorder(@PathVariable Long id, Actor actor, UriComponentsBuilder uriBuilder) {
        return created(facade.reorder(id, actor), uriBuilder);
    }

    /** Staff: PLACED to READY (the recipe is prepared). */
    @PostMapping("/{id}/prepare")
    public OrderResponse prepare(@PathVariable Long id, Actor actor) {
        facade.prepareOrder(id, actor);
        return OrderResponse.from(facade.getOrder(id));
    }

    /** Staff: READY to FULFILLED; the order must have been paid. */
    @PostMapping("/{id}/fulfil")
    public OrderResponse fulfil(@PathVariable Long id, Actor actor) {
        facade.fulfillOrder(id, actor);
        return OrderResponse.from(facade.getOrder(id));
    }

    /** Staff: cancel an in-progress order. */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Actor actor) {
        facade.cancelOrder(id, actor);
        return OrderResponse.from(facade.getOrder(id));
    }

    /**
     * Pays a READY order. Staff (at the counter) or the order's own customer. 200 with the result when paid;
     * <b>402 with the same result body when the gateway declines</b> (the order stays READY and may be paid
     * again). Not READY / already paid is 409.
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentResponse> pay(@PathVariable Long id, @Valid @RequestBody PayHttpRequest request, Actor actor) {
        PaymentResult result = facade.payOrder(id, request.provider(), actor);
        HttpStatus status = result.isPaid() ? HttpStatus.OK : HttpStatus.PAYMENT_REQUIRED;
        return ResponseEntity.status(status).body(PaymentResponse.from(result));
    }

    private static ResponseEntity<OrderResponse> created(Order order, UriComponentsBuilder uriBuilder) {
        URI location = uriBuilder.path("/api/orders/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }
}
