package dev.saberlabs.coffeechat.support;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Builders for <em>unit</em> tests that need an entity with a generated id but no database: the
 * {@code id} is assigned reflectively, as Hibernate would after an insert. Tests that go through
 * the database build their fixtures with the repositories instead.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot assign id on " + entity.getClass(), e);
        }
    }

    public static UserEntity customer(long id) {
        return withId(new UserEntity("Customer " + id, Role.CUSTOMER), id);
    }

    /** A plain-espresso order with the given id, at {@code PLACED}. */
    public static OrderEntity placedEspresso(long orderId, UserEntity customer) {
        Instant now = Instant.now();
        PriceBreakdown price = PriceBreakdown.of(new BigDecimal("2.50"), new BigDecimal("0.00"), new BigDecimal("0.00"));
        return withId(new OrderEntity(customer, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED,
                LoyaltyTier.REGULAR, price, now, now), orderId);
    }
}
