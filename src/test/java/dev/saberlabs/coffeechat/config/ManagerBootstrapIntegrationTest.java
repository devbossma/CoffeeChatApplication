package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.service.StaffService;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ManagerBootstrap (real database)")
class ManagerBootstrapIntegrationTest extends AbstractIntegrationTest {

    @Autowired StaffService staff;

    @Test
    @DisplayName("seeds exactly one MANAGER, however many times it runs, and never when one already exists")
    void idempotent() {
        ManagerBootstrap bootstrap = new ManagerBootstrap(users, staff, "Boss");

        UserEntity first = bootstrap.seed();
        assertNotNull(first);
        assertNull(bootstrap.seed());
        assertNull(new ManagerBootstrap(users, staff, "Another").seed());

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_accounts WHERE role = 'MANAGER'", Long.class));
        assertEquals("Boss", users.findById(first.id()).orElseThrow().name());
        assertEquals(Role.MANAGER, users.findById(first.id()).orElseThrow().role());
    }

    @Test
    @DisplayName("the running application seeded nothing: the property is unset by default")
    void notSeededByDefault() {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_accounts", Long.class));
    }
}
