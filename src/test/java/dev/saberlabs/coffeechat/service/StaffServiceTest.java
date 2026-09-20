package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("StaffService")
class StaffServiceTest extends AbstractIntegrationTest {

    @Autowired StaffService service;

    @Nested
    @DisplayName("createBarista()")
    class CreateBaristaTests {

        @Test
        @DisplayName("persists a BARISTA")
        void creates() {
            UserEntity created = service.createBarista("Bob");
            assertNotNull(created.id());
            assertEquals(Role.BARISTA, users.findById(created.id()).orElseThrow().role());
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> service.createBarista(" "));
        }
    }

    @Nested
    @DisplayName("createManager()")
    class CreateManagerTests {

        @Test
        @DisplayName("persists a MANAGER")
        void creates() {
            UserEntity created = service.createManager("Maria");
            assertEquals(Role.MANAGER, users.findById(created.id()).orElseThrow().role());
        }

        @Test
        @DisplayName("rejects a null name")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> service.createManager(null));
        }
    }

    @Test
    @DisplayName("constructor rejects a null repository")
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new StaffService(null));
    }
}
