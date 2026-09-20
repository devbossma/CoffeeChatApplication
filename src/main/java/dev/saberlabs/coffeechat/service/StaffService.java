package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Creates staff accounts (BARISTA, MANAGER) over {@link UserRepository}, the counterpart of
 * {@link CustomerService}. Deliberately has no REST endpoint yet: with no authentication, an open
 * endpoint that mints managers would defeat the role boundary. Part 03 Step 6 decides how the first
 * manager comes to exist.
 */
@Service
public class StaffService {

    private final UserRepository users;

    public StaffService(@NotNull UserRepository users) {
        this.users = Objects.requireNonNull(users, "users cannot be null");
    }

    /** @throws IllegalArgumentException if {@code name} is blank */
    @Transactional
    public UserEntity createBarista(@NotNull String name) {
        return users.save(new UserEntity(name, Role.BARISTA));
    }

    /** @throws IllegalArgumentException if {@code name} is blank */
    @Transactional
    public UserEntity createManager(@NotNull String name) {
        return users.save(new UserEntity(name, Role.MANAGER));
    }
}
