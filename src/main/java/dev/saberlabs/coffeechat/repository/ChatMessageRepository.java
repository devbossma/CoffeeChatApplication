package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * A session's full history, oldest first; the id breaks a same-instant tie so messages written
     * back to back keep their write order. Served by {@code idx_chat_messages_session_sent}.
     */
    List<ChatMessageEntity> findBySessionIdOrderBySentAtAscIdAsc(Long sessionId);

    /** One page of the same ordering (same index), for the paged history endpoint. */
    List<ChatMessageEntity> findBySessionIdOrderBySentAtAscIdAsc(Long sessionId, Pageable pageable);
}
