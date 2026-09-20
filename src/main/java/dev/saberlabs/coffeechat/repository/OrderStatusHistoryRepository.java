package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {

    /** An order's full audit trail, oldest first. Served by {@code idx_order_status_history_order_changed}. */
    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAsc(Long orderId);
}
