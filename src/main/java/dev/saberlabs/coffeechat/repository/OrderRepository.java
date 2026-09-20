package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /**
     * Orders currently in any of the given statuses. Backs Part 03 Step 3's restart-recovery
     * query (re-enqueueing orphaned {@code PLACED}/{@code PREPARING} rows after a crash, since
     * {@code OrderQueue} is in-memory and forgets everything on restart) &mdash; served by
     * {@code idx_orders_status}.
     */
    /**
     * Loads the order with a row lock ({@code SELECT ... FOR UPDATE}) for the duration of the
     * caller's transaction. Used by payment so two concurrent payers of one order are serialised
     * BEFORE either calls the gateway: the second blocks, then sees the first's PAID row and is
     * rejected without being charged.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findForUpdateById(@Param("id") Long id);

    List<OrderEntity> findByStatusIn(List<OrderStatus> statuses);

    /**
     * Served by {@code idx_orders_customer_id}. The extras are fetched in the same query so mapping N
     * orders to snapshots costs one statement, not N+1.
     */
    @EntityGraph(attributePaths = "extras")
    List<OrderEntity> findByCustomerId(Long customerId);
}
