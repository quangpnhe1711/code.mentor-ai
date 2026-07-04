package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshotStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorySnapshotJpaRepository extends JpaRepository<RepositorySnapshot, UUID> {

    List<RepositorySnapshot> findByRepositoryId(UUID repositoryId);

    Optional<RepositorySnapshot> findFirstByOrganizationIdAndRepositoryIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, UUID repositoryId, RepositorySnapshotStatus status);
}
