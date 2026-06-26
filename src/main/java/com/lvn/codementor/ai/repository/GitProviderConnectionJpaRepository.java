package com.lvn.codementor.ai.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitProviderConnectionJpaRepository extends JpaRepository<GitProviderConnection, UUID> {

    Optional<GitProviderConnection> findByProviderAndProviderAccountIdAndUserId(
            RepositoryProvider provider, String providerAccountId, UUID userId);
}
