package dev.saberlabs.coffeechat.repository;

import dev.saberlabs.coffeechat.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /** A session's full history, oldest first. Served by {@code idx_chat_messages_session_sent}. */
    List<ChatMessageEntity> findBySessionIdOrderBySentAtAsc(Long sessionId);
}
