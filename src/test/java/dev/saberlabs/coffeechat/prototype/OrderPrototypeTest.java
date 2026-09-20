package dev.saberlabs.coffeechat.prototype;

import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderPrototype")
class OrderPrototypeTest {

    private OrderPrototype prototype;

    @BeforeEach
    void setUp() {
        prototype = new OrderPrototype();
    }

    private static Order fulfilledOrder() {
        Instant now = Instant.now();
        return new Order(99L, 3L, CoffeeType.ESPRESSO, List.of(ExtraType.MILK), "Espresso with milk",
                PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.50"), new BigDecimal("0.00")),
                LoyaltyTier.REGULAR, OrderStatus.FULFILLED, now, now);
    }

    @Nested
    @DisplayName("copyOf()")
    class CopyOfTests {

        @Test
        @DisplayName("copies the customer id, base coffee type and extras")
        void copiesStructure() {
            prototype.copyOf(fulfilledOrder());
            assertEquals(3L, prototype.customerId());
            assertEquals(CoffeeType.ESPRESSO, prototype.type());
            assertEquals(List.of(ExtraType.MILK), prototype.extras());
        }

        @Test
        @DisplayName("does not carry over id, status or timestamps (they are not on the prototype at all)")
        void dropsIdentityAndLifecycle() {
            prototype.copyOf(fulfilledOrder());
            // the prototype only exposes structure; re-placing goes through placeOrder which
            // assigns a fresh id and PLACED status
            PlaceOrderRequest request = prototype.toPlaceOrderRequest();
            assertEquals(CoffeeType.ESPRESSO, request.type());
            assertEquals(List.of(ExtraType.MILK), request.extras());
        }

        @Test
        @DisplayName("takes an independent copy of the extras list")
        void independentExtras() {
            Order original = fulfilledOrder();
            prototype.copyOf(original);
            assertThrows(UnsupportedOperationException.class,
                    () -> prototype.extras().add(ExtraType.SUGAR));
        }

        @Test
        @DisplayName("keeps duplicate extras and their order (rebuilt from the persisted list, not a description)")
        void preservesOrderedDuplicateExtras() {
            Instant now = Instant.now();
            Order original = new Order(99L, 3L, CoffeeType.LATTE,
                    List.of(ExtraType.MILK, ExtraType.SUGAR, ExtraType.MILK), "Latte",
                    PriceBreakdown.of(new BigDecimal("3.50"), new BigDecimal("1.50"), new BigDecimal("0.00")),
                    LoyaltyTier.GOLD, OrderStatus.READY, now, now);

            prototype.copyOf(original);

            assertEquals(List.of(ExtraType.MILK, ExtraType.SUGAR, ExtraType.MILK), prototype.extras());
        }

        @Test
        @DisplayName("rejects a null order")
        void rejectsNull() {
            assertThrows(NullPointerException.class, () -> prototype.copyOf(null));
        }
    }

    @Nested
    @DisplayName("toPlaceOrderRequest()")
    class ToPlaceOrderRequestTests {

        @Test
        @DisplayName("builds a request matching the seeded order")
        void buildsRequest() {
            prototype.copyOf(fulfilledOrder());
            PlaceOrderRequest request = prototype.toPlaceOrderRequest();
            assertEquals(3L, request.customerId());
            assertEquals(CoffeeType.ESPRESSO, request.type());
        }

        @Test
        @DisplayName("throws if the prototype has not been seeded")
        void throwsWhenNotSeeded() {
            assertThrows(IllegalStateException.class, () -> prototype.toPlaceOrderRequest());
        }

        @Test
        @DisplayName("a fresh prototype has empty extras")
        void freshHasEmptyExtras() {
            assertTrue(prototype.extras().isEmpty());
        }
    }
}
