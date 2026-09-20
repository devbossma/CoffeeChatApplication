package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("StaffAccess")
class StaffAccessTest extends AbstractIntegrationTest {

    @Autowired StaffAccess access;

    @Nested
    @DisplayName("authorize()")
    class AuthorizeTests {

        @Test
        @DisplayName("a BARISTA is allowed and is what gets recorded as changed_by")
        void baristaRecorded() {
            UserEntity barista = barista("Bob");
            assertEquals(barista.id(), access.authorize(Actor.user(barista.id()), StaffAccess.STAFF));
        }

        @Test
        @DisplayName("a MANAGER is allowed but NOT recorded (changed_by stays NULL for manager-driven changes)")
        void managerNotRecorded() {
            UserEntity manager = manager("Maria");
            assertNull(access.authorize(Actor.user(manager.id()), StaffAccess.STAFF));
        }

        @Test
        @DisplayName("the SYSTEM is allowed without any lookup and is not recorded")
        void systemAllowed() {
            assertNull(access.authorize(Actor.SYSTEM, StaffAccess.STAFF));
        }

        @Test
        @DisplayName("a CUSTOMER is rejected with a RoleNotAllowedException naming the user and role")
        void customerRejected() {
            UserEntity customer = customer("Alice");
            RoleNotAllowedException e = assertThrows(RoleNotAllowedException.class,
                    () -> access.authorize(Actor.user(customer.id()), StaffAccess.STAFF));
            assertEquals(customer.id(), e.userId());
            assertEquals(Role.CUSTOMER, e.role());
            assertTrue(e.getMessage().contains("CUSTOMER"));
        }

        @Test
        @DisplayName("an unknown user is an UnknownActorException, distinguishable from a wrong role")
        void unknown() {
            assertThrows(UnknownActorException.class, () -> access.authorize(Actor.user(987_654L), StaffAccess.STAFF));
        }

        @Test
        @DisplayName("honours the allowed set it is given (a MANAGER-only action rejects a BARISTA)")
        void customAllowedSet() {
            UserEntity barista = barista("Bob");
            assertThrows(RoleNotAllowedException.class,
                    () -> access.authorize(Actor.user(barista.id()), EnumSet.of(Role.MANAGER)));
        }

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> access.authorize(null, StaffAccess.STAFF));
            assertThrows(NullPointerException.class, () -> access.authorize(Actor.SYSTEM, null));
        }
    }

    @Nested
    @DisplayName("authorizePayer()")
    class AuthorizePayerTests {

        private OrderEntity orderOf(UserEntity customer) {
            Instant now = Instant.now();
            return orders.saveAndFlush(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(), OrderStatus.READY,
                    LoyaltyTier.REGULAR, PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO), now, now));
        }

        @Test
        @DisplayName("staff, the order's own customer and the system may pay")
        void allowed() {
            UserEntity alice = customer("Alice");
            OrderEntity order = orderOf(alice);
            access.authorizePayer(Actor.user(barista("Bob").id()), order);
            access.authorizePayer(Actor.user(manager("Maria").id()), order);
            access.authorizePayer(Actor.user(alice.id()), order);
            access.authorizePayer(Actor.SYSTEM, order);
        }

        @Test
        @DisplayName("a different customer is rejected with a RoleNotAllowedException")
        void otherCustomer() {
            OrderEntity order = orderOf(customer("Alice"));
            UserEntity mallory = customer("Mallory");
            assertThrows(RoleNotAllowedException.class, () -> access.authorizePayer(Actor.user(mallory.id()), order));
        }

        @Test
        @DisplayName("an unknown user is an UnknownActorException")
        void unknown() {
            OrderEntity order = orderOf(customer("Alice"));
            assertThrows(UnknownActorException.class, () -> access.authorizePayer(Actor.user(987_654L), order));
        }

        @Test
        @DisplayName("rejects null arguments")
        void rejectsNulls() {
            OrderEntity order = orderOf(customer("Alice"));
            assertThrows(NullPointerException.class, () -> access.authorizePayer(null, order));
            assertThrows(NullPointerException.class, () -> access.authorizePayer(Actor.SYSTEM, null));
        }
    }

    @Test
    @DisplayName("constructor rejects a null repository")
    void rejectsNullRepository() {
        assertThrows(NullPointerException.class, () -> new StaffAccess(null));
    }
}
