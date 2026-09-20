package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UserRepository")
class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("saves and reloads a user with fulfilledOrders defaulting to 0")
        void savesAndReloads() {
            UserEntity saved = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
            entityManager.clear();

            UserEntity reloaded = userRepository.findById(saved.id()).orElseThrow();
            assertEquals("Alice", reloaded.name());
            assertEquals(Role.CUSTOMER, reloaded.role());
            assertEquals(0, reloaded.fulfilledOrders());
        }

        @Test
        @DisplayName("rejects a null name at the database level")
        void rejectsNullName() {
            assertConstraintViolation("INSERT INTO user_accounts (name, role) VALUES (NULL, 'CUSTOMER')",
                    NOT_NULL_VIOLATION, "name");
        }

        @Test
        @DisplayName("rejects a role outside CUSTOMER/BARISTA/MANAGER at the database level")
        void rejectsInvalidRole() {
            assertConstraintViolation("INSERT INTO user_accounts (name, role) VALUES ('Bob', 'ROBOT')",
                    CHECK_VIOLATION, "chk_user_role");
        }

        @Test
        @DisplayName("rejects a negative fulfilled_orders at the database level")
        void rejectsNegativeFulfilledOrders() {
            assertConstraintViolation(
                    "INSERT INTO user_accounts (name, role, fulfilled_orders) VALUES ('Bob', 'CUSTOMER', -1)",
                    CHECK_VIOLATION, "chk_user_fulfilled_orders_nonneg");
        }
    }

    @Nested
    @DisplayName("incrementFulfilledOrders()")
    class IncrementFulfilledOrdersTests {

        @Test
        @DisplayName("atomically increments the count, not a read-modify-write")
        void incrementsAtomically() {
            UserEntity saved = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));

            int updated = userRepository.incrementFulfilledOrders(saved.id());
            entityManager.clear();

            assertEquals(1, updated);
            assertEquals(1, userRepository.findById(saved.id()).orElseThrow().fulfilledOrders());
        }

        @Test
        @DisplayName("two successive increments both apply")
        void twoIncrementsBothApply() {
            UserEntity saved = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));

            userRepository.incrementFulfilledOrders(saved.id());
            userRepository.incrementFulfilledOrders(saved.id());
            entityManager.clear();

            assertEquals(2, userRepository.findById(saved.id()).orElseThrow().fulfilledOrders());
        }

        @Test
        @DisplayName("returns 0 and changes nothing for an unknown id")
        void returnsZeroForUnknownId() {
            assertEquals(0, userRepository.incrementFulfilledOrders(404L));
        }
    }

    @Nested
    @DisplayName("loyaltyTier() (derived, not stored)")
    class LoyaltyTierTests {

        @Test
        @DisplayName("reflects fulfilledOrders after reload, not a stored column")
        void derivedAfterReload() {
            UserEntity saved = userRepository.saveAndFlush(new UserEntity("Alice", Role.CUSTOMER));
            for (int i = 0; i < 6; i++) {
                userRepository.incrementFulfilledOrders(saved.id());
            }
            entityManager.clear();

            UserEntity reloaded = userRepository.findById(saved.id()).orElseThrow();
            assertTrue(reloaded.loyaltyTier().name().equals("SILVER"));
        }
    }
}
