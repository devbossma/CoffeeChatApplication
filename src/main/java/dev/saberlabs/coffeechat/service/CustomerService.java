package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.CustomerNotFoundException;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Customer operations over {@link UserRepository}. A "customer" is a {@code UserEntity} whose role
 * is {@code CUSTOMER}; a barista or manager id is never a customer, so it looks up as not found.
 * Loyalty tier is derived ({@code UserEntity.loyaltyTier()}), never stored, and
 * {@code fulfilledOrders} is only ever changed by {@code UserRepository.incrementFulfilledOrders}.
 */
@Service
public class CustomerService {

    private final UserRepository users;

    public CustomerService(@NotNull UserRepository users) {
        this.users = Objects.requireNonNull(users, "users cannot be null");
    }

    /**
     * @throws IllegalArgumentException if {@code name} is blank (from the {@link UserEntity} constructor)
     */
    @Transactional
    public UserEntity create(@NotNull String name) {
        return users.save(new UserEntity(name, Role.CUSTOMER));
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findById(@NotNull Long id) {
        Objects.requireNonNull(id, "id cannot be null");
        return users.findById(id).filter(u -> u.role() == Role.CUSTOMER);
    }

    /**
     * Loads the customer inside the calling command's transaction.
     *
     * @throws CustomerNotFoundException if no CUSTOMER user has that id
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UserEntity require(@NotNull Long id) {
        return findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
