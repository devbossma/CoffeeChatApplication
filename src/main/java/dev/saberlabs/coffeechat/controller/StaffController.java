package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.StaffMember;
import dev.saberlabs.coffeechat.model.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Staff creation, MANAGER only (enforced by the facade). The first manager is seeded from configuration at
 * startup ({@code coffeeshop.bootstrap.manager-name}), so an empty system can still be brought up.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final CoffeeShopFacade facade;

    public StaffController(CoffeeShopFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request, Actor actor,
                                                UriComponentsBuilder uriBuilder) {
        StaffMember created = facade.createStaff(actor, request.name(), Role.valueOf(request.role().name()));
        URI location = uriBuilder.path("/api/staff/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(StaffResponse.from(created));
    }
}
