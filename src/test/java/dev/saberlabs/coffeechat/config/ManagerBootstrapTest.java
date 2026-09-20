package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ManagerBootstrap")
class ManagerBootstrapTest {

    private UserRepository users;
    private StaffService staff;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        staff = mock(StaffService.class);
    }

    @Test
    @DisplayName("with the property unset or blank nothing is looked up or created (the default: tests never seed)")
    void unset() {
        assertNull(new ManagerBootstrap(users, staff, "").seed());
        assertNull(new ManagerBootstrap(users, staff, "   ").seed());
        assertNull(new ManagerBootstrap(users, staff, null).seed());

        verifyNoInteractions(users, staff);
    }

    @Test
    @DisplayName("with the property set and no manager yet, one is created with the (stripped) name")
    void seeds() {
        UserEntity manager = new UserEntity("Boss", Role.MANAGER);
        when(users.existsByRole(Role.MANAGER)).thenReturn(false);
        when(staff.createManager("Boss")).thenReturn(manager);

        assertEquals(manager, new ManagerBootstrap(users, staff, "  Boss ").seed());
    }

    @Test
    @DisplayName("idempotent: with a manager already present nothing is created")
    void alreadyThere() {
        when(users.existsByRole(Role.MANAGER)).thenReturn(true);

        assertNull(new ManagerBootstrap(users, staff, "Boss").seed());

        verify(staff, never()).createManager("Boss");
    }

    @Test
    @DisplayName("run() seeds as an ApplicationRunner")
    void runs() {
        when(users.existsByRole(Role.MANAGER)).thenReturn(false);
        when(staff.createManager("Boss")).thenReturn(new UserEntity("Boss", Role.MANAGER));

        new ManagerBootstrap(users, staff, "Boss").run(new DefaultApplicationArguments());

        verify(staff).createManager("Boss");
    }

    @Test
    @DisplayName("constructor rejects null collaborators")
    void nulls() {
        assertThrows(NullPointerException.class, () -> new ManagerBootstrap(null, staff, "x"));
        assertThrows(NullPointerException.class, () -> new ManagerBootstrap(users, null, "x"));
    }
}
