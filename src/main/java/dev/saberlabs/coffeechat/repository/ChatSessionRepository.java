package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatSessionEntity;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    /**
     * Sessions currently in the given status. Backs Part 03 Step 5's restart-recovery decision
     * for orphaned {@code WAITING} sessions ({@code BaristaQueue} is in-memory and forgets
     * everything on restart) &mdash; served by {@code idx_chat_sessions_status}.
     */
    List<ChatSessionEntity> findByStatus(SessionStatus status);

    /**
     * The customer's current non-INACTIVE session, if any &mdash; mirrors
     * {@code uq_chat_sessions_active_customer}, the partial unique index enforcing at most one of
     * these per customer.
     */
    Optional<ChatSessionEntity> findByCustomerIdAndStatusNot(Long customerId, SessionStatus status);
}
