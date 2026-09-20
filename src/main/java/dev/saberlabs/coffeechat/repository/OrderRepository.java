package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /**
     * Orders currently in any of the given statuses. Backs Part 03 Step 3's restart-recovery
     * query (re-enqueueing orphaned {@code PLACED}/{@code PREPARING} rows after a crash, since
     * {@code OrderQueue} is in-memory and forgets everything on restart) &mdash; served by
     * {@code idx_orders_status}.
     */
    List<OrderEntity> findByStatusIn(List<OrderStatus> statuses);

    /** Served by {@code idx_orders_customer_id}. */
    List<OrderEntity> findByCustomerId(Long customerId);
}
