package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatSessionEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    /**
     * Sessions currently in the given status. Backs Part 03 Step 5's restart-recovery decision
     * for orphaned {@code WAITING} sessions ({@code BaristaQueue} is in-memory and forgets
     * everything on restart) &mdash; served by {@code idx_chat_sessions_status}.
     */
    List<ChatSessionEntity> findByStatus(SessionStatus status);

    /** Sessions in {@code status}, oldest first (creation order), for restart recovery. */
    List<ChatSessionEntity> findByStatusOrderByIdAsc(SessionStatus status);

    /**
     * The one write that turns a WAITING session into an ACTIVE one with a barista. Conditional on the
     * session still being WAITING, so a session that was ended (or already matched) in the meantime is
     * left alone.
     *
     * @return 1 if the session was activated, 0 if it was no longer WAITING
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSessionEntity s set s.status = dev.saberlabs.coffeechat.model.SessionStatus.ACTIVE, "
            + "s.barista = :barista where s.id = :id and s.status = dev.saberlabs.coffeechat.model.SessionStatus.WAITING")
    int activateIfWaiting(@Param("id") Long id, @Param("barista") UserEntity barista);

    /**
     * Ends a session. Conditional on it not already being INACTIVE, so of two concurrent ends exactly one
     * gets 1 back (and so exactly one frees the barista).
     *
     * @return 1 if the session was ended by this call, 0 if it was already INACTIVE (or does not exist)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatSessionEntity s set s.status = dev.saberlabs.coffeechat.model.SessionStatus.INACTIVE "
            + "where s.id = :id and s.status <> dev.saberlabs.coffeechat.model.SessionStatus.INACTIVE")
    int endIfNotInactive(@Param("id") Long id);

    /**
     * The customer's current non-INACTIVE session, if any &mdash; mirrors
     * {@code uq_chat_sessions_active_customer}, the partial unique index enforcing at most one of
     * these per customer.
     */
    Optional<ChatSessionEntity> findByCustomerIdAndStatusNot(Long customerId, SessionStatus status);
}
