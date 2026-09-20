package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /** Whether any user has this role (used to seed the first MANAGER only when there is none). */
    boolean existsByRole(Role role);

    /**
     * Atomically increments {@code fulfilled_orders} in the database (a single
     * {@code UPDATE ... SET fulfilled_orders = fulfilled_orders + 1}), not a Java-side
     * read-modify-write &mdash; two baristas concurrently fulfilling two different orders for the
     * same customer never race, and this table needs no {@code @Version} column because of it.
     *
     * <p>Must be called within a transaction (the caller's {@code @Transactional} command
     * execution, in production; {@code @DataJpaTest}'s implicit per-test transaction, in tests).
     *
     * @return the number of rows updated (0 if no user has that id, 1 otherwise)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.fulfilledOrders = u.fulfilledOrders + 1 WHERE u.id = :id")
    int incrementFulfilledOrders(@Param("id") Long id);
}
