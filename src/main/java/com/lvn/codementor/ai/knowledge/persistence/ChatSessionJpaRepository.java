package com.lvn.codementor.ai.knowledge.persistence;

import com.lvn.codementor.ai.knowledge.domain.ChatSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionJpaRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findByOrganizationIdAndRepositoryIdOrderByCreatedAtDesc(UUID organizationId, UUID repositoryId);

    Optional<ChatSession> findByIdAndOrganizationIdAndRepositoryId(UUID id, UUID organizationId, UUID repositoryId);
}
