package com.lvn.codementor.ai.knowledge.persistence;

import com.lvn.codementor.ai.knowledge.domain.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(UUID chatSessionId);
}
