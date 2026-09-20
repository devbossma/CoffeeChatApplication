package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    /** An order's full audit trail, oldest first (id breaks a same-instant tie, so rows written by one command keep
     * their write order). Served by {@code idx_order_status_history_order_changed}. */
    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
}
