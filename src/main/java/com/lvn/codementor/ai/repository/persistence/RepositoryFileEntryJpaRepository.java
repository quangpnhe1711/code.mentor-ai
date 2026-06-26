package com.lvn.codementor.ai.repository.persistence;

import com.lvn.codementor.ai.repository.domain.RepositoryFileEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryFileEntryJpaRepository extends JpaRepository<RepositoryFileEntry, UUID> {

    List<RepositoryFileEntry> findBySnapshotId(UUID snapshotId);

    List<RepositoryFileEntry> findBySnapshotIdAndIncluded(UUID snapshotId, boolean included);
}
