package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.StaffService;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Seeds the first MANAGER so an empty system can be brought up (only a manager can create staff, and there is
 * no other way to make one). Driven by ONE property, {@code coffeeshop.bootstrap.manager-name}:
 * unset or blank means no seeding (the default, so tests and production-like runs never seed); set, and if no
 * MANAGER exists yet, one is created. Idempotent: a restart, or a second run, creates nothing.
 *
 * <p>The seeded manager's id is logged at INFO because, in this demo identity model (PRD section 4), the id
 * <em>is</em> the credential ({@code X-User-Id}). It is enabled in {@code application-local.properties}.
 */
@Component
public class ManagerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerBootstrap.class);

    private final UserRepository users;
    private final StaffService staff;
    private final String managerName;

    public ManagerBootstrap(@NotNull UserRepository users, @NotNull StaffService staff,
                            @Value("${coffeeshop.bootstrap.manager-name:}") String managerName) {
        this.users = Objects.requireNonNull(users, "users cannot be null");
        this.staff = Objects.requireNonNull(staff, "staff cannot be null");
        this.managerName = managerName == null ? "" : managerName.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** @return the seeded manager, or null if nothing was created */
    UserEntity seed() {
        if (managerName.isEmpty()) {
            return null;
        }
        if (users.existsByRole(Role.MANAGER)) {
            log.info("A manager already exists; not seeding '{}'", managerName);
            return null;
        }
        UserEntity manager = staff.createManager(managerName);
        log.info("Seeded initial manager '{}' with id {}: send it as the X-User-Id header", manager.name(), manager.id());
        return manager;
    }
}
