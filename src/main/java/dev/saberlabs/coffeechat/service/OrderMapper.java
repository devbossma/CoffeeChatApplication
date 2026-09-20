package dev.saberlabs.coffeechat.service;

import dev.saberlabs.coffeechat.decorator.CoffeeDecorators;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.factory.CoffeeFactory;
import dev.saberlabs.coffeechat.model.Order;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Entity -&gt; snapshot, and only that direction: an {@code OrderEntity} is created solely by
 * {@link OrderService#create}, so there is no second path that builds one from a snapshot.
 *
 * <p>Must be called while the entity's persistence context is open (inside the service's
 * transaction): it reads the lazy {@code customer} and {@code extras}. The coffee description is
 * rebuilt from the base type and extras via Factory + Decorator, never stored.
 */
@Component
public class OrderMapper {

    private final CoffeeFactory coffeeFactory;

    public OrderMapper(@NotNull CoffeeFactory coffeeFactory) {
        this.coffeeFactory = Objects.requireNonNull(coffeeFactory, "coffeeFactory cannot be null");
    }

    public Order toSnapshot(@NotNull OrderEntity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        var extras = entity.extras();
        String description = CoffeeDecorators
                .decorate(coffeeFactory.create(entity.baseCoffeeType()), extras)
                .description();
        return new Order(
                entity.id(),
                entity.customerId(),
                entity.baseCoffeeType(),
                extras,
                description,
                entity.price(),
                entity.appliedLoyaltyTier(),
                entity.status(),
                entity.placedAt(),
                entity.updatedAt());
    }
}
