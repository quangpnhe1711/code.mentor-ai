package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorySnapshotJpaRepository extends JpaRepository<RepositorySnapshot, UUID> {

    List<RepositorySnapshot> findByRepositoryId(UUID repositoryId);
}
