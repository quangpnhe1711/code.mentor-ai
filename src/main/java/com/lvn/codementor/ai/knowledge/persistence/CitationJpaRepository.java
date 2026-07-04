package com.lvn.codementor.ai.knowledge.persistence;

import com.lvn.codementor.ai.knowledge.domain.Citation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitationJpaRepository extends JpaRepository<Citation, UUID> {

    List<Citation> findByChatMessageIdOrderByCreatedAtAsc(UUID chatMessageId);
}
