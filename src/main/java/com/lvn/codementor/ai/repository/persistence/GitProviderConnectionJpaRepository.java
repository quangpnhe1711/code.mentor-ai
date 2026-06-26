package com.lvn.codementor.ai.repository.persistence;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitProviderConnectionJpaRepository extends JpaRepository<GitProviderConnection, UUID> {

    Optional<GitProviderConnection> findByProviderAndProviderAccountIdAndUserId(
            RepositoryProvider provider, String providerAccountId, UUID userId);

    /** The user's connection for a provider (a user has at most one per provider in Foundation). */
    Optional<GitProviderConnection> findFirstByUserIdAndProvider(UUID userId, RepositoryProvider provider);
}
