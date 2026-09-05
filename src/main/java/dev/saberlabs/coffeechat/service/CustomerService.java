package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.model.Customer;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory store of customers.
 *
 * <p><strong>Part 01 stand-in for a Part 03 Spring Data JPA {@code CustomerRepository}.</strong>
 * Same rationale as {@link OrderService}.
 */
@Service
public class CustomerService {

    private final ConcurrentMap<Long, Customer> customers = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * Creates and stores a new customer with 0 fulfilled orders.
     *
     * @throws IllegalArgumentException if {@code name} is blank (from the {@link Customer} constructor)
     */
    public Customer create(String name) {
        Customer customer = new Customer(name);
        customer.assignId(sequence.incrementAndGet());
        customers.put(customer.id(), customer);
        return customer;
    }

    public Optional<Customer> findById(Long id) {
        return Optional.ofNullable(customers.get(id));
    }

    /**
     * Records one more fulfilled order for the customer (Strategy: may raise the derived tier).
     *
     * @return the updated customer
     * @throws NoSuchElementException if no customer has that id
     */
    public Customer incrementFulfilled(Long id) {
        Customer customer = customers.get(id);
        if (customer == null) {
            throw new NoSuchElementException("No customer with id " + id);
        }
        customer.incrementFulfilled();
        return customer;
    }

    /** Test/reset aid. */
    public void clear() {
        customers.clear();
        sequence.set(0);
    }
}
