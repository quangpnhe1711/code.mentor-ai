package com.lvn.codementor.ai.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenJpaRepository extends JpaRepository<AuthRefreshToken, UUID> {

    /** Look up a presented refresh token by its hash (the raw token is never stored). */
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);
}
