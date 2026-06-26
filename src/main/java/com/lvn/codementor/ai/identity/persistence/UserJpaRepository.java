package com.lvn.codementor.ai.identity.persistence;
import com.lvn.codementor.ai.identity.domain.User;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGithubUserId(String githubUserId);
}
