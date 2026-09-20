package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    /** At most one row: {@code UNIQUE(order_id)}. */
    Optional<PaymentEntity> findByOrderId(Long orderId);
}
