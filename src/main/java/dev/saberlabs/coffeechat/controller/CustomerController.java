package dev.saberlabs.coffeechat.controller;

import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * REST surface for customers (PRD &sect;9.4). Thin: validate, delegate to {@code CustomerService},
 * map to a response.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        Customer customer = customers.create(request.name());
        URI location = uriBuilder.path("/api/customers/{id}").buildAndExpand(customer.id()).toUri();
        return ResponseEntity.created(location).body(CustomerResponse.from(customer));
    }
}
