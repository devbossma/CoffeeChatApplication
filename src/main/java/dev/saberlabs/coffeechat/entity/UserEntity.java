package dev.saberlabs.coffeechat.entity;

import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

/**
 * The one identity table for every person: customers, baristas, and managers, distinguished by
 * {@link #role()}. Backs {@code ChatSessionEntity.customerId}/{@code baristaId} (neither of which
 * had a real entity to reference before Part 03) and {@code OrderEntity.customerId}.
 *
 * <p>{@code fulfilledOrders} lives here rather than on a separate {@code CustomerEntity} &mdash;
 * see the package-level javadoc for why. It is only ever meaningful for {@code Role.CUSTOMER}
 * rows; the DB only enforces {@code >= 0}, not role-conditional meaning, so the application layer
 * (Step 3) must not read it for a BARISTA/MANAGER row.
 *
 * <p>{@link #fulfilledOrders} is mutated exclusively through
 * {@code UserRepository.incrementFulfilledOrders(Long)}, an atomic {@code UPDATE ... SET
 * fulfilled_orders = fulfilled_orders + 1}, not a read-modify-write on this entity &mdash; two
 * baristas concurrently fulfilling two different orders for the same customer never race, and no
 * {@code @Version} column is needed on this table because of it.
 */
@Entity
@Table(name = "user_accounts")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "fulfilled_orders", nullable = false)
    private long fulfilledOrders;

    /** For JPA/Hibernate only. */
    protected UserEntity() {
    }

    public UserEntity(@NotNull String name, @NotNull Role role) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.fulfilledOrders = 0;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Role role() {
        return role;
    }

    public long fulfilledOrders() {
        return fulfilledOrders;
    }

    /** The tier derived from {@link #fulfilledOrders()} &mdash; meaningful for CUSTOMER rows only. */
    public LoyaltyTier loyaltyTier() {
        return LoyaltyTier.forCount(fulfilledOrders);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof UserEntity other && id != null && id.equals(other.id());
    }

    /**
     * A constant, not {@code Objects.hashCode(id)}: {@code id} is null until first persisted, so
     * a hashCode derived from it would change after the entity moves into a {@code HashSet}/
     * {@code HashMap} it was already added to before persisting, silently making it unfindable by
     * its own bucket. Equality is still id-based via {@link #equals}; only the hash bucket is
     * fixed for the entity's whole lifecycle. The standard JPA identifier-equality pattern.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UserEntity[id=%s, name=%s, role=%s, fulfilledOrders=%d]"
                .formatted(id, name, role, fulfilledOrders);
    }
}
