package com.lvn.codementor.ai.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lvn.codementor.ai.identity.persistence.UserJpaRepository;
import com.lvn.codementor.ai.organization.persistence.OrganizationJpaRepository;
import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.organization.domain.OrganizationType;
import com.lvn.codementor.ai.repository.domain.GitProviderConnection;
import com.lvn.codementor.ai.repository.persistence.GitProviderConnectionJpaRepository;
import com.lvn.codementor.ai.repository.application.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.support.AbstractPostgresIT;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** First-login provisioning behaviour (doc 14 §5): idempotency, encrypted tokens, hashed refresh tokens. */
class FirstLoginProvisioningIT extends AbstractPostgresIT {

    @Autowired
    FirstLoginProvisioningService provisioningService;

    @Autowired
    UserJpaRepository users;

    @Autowired
    OrganizationJpaRepository organizations;

    @Autowired
    OrganizationMemberJpaRepository members;

    @Autowired
    GitProviderConnectionJpaRepository connections;

    @Autowired
    GitProviderCredentialStore credentialStore;

    @Autowired
    RefreshTokenHasher refreshTokenHasher;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void firstLoginIsIdempotent() {
        String githubUserId = "gh-" + UUID.randomUUID();
        GitHubOAuthResult result = sample(githubUserId, "access-token", "refresh-token");

        ProvisioningOutcome first = provisioningService.provisionFromGitHub(result);
        ProvisioningOutcome second = provisioningService.provisionFromGitHub(result);

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.personalOrganizationId()).isEqualTo(first.personalOrganizationId());

        UUID userId = first.userId();
        assertThat(users.findByGithubUserId(githubUserId)).isPresent();
        assertThat(organizations.findByOwnerUserIdAndType(userId, OrganizationType.PERSONAL)).isPresent();
        assertThat(members.findByOrganizationIdAndUserId(first.personalOrganizationId(), userId)).isPresent();

        assertThat(countFor("organizations", "owner_user_id", userId)).isEqualTo(1);
        assertThat(countFor("organization_members", "user_id", userId)).isEqualTo(1);
        assertThat(countFor("git_provider_connections", "user_id", userId)).isEqualTo(1);
    }

    @Test
    void accessTokenIsStoredEncryptedNotPlaintext() {
        String githubUserId = "gh-" + UUID.randomUUID();
        String plaintextToken = "ghp_PLAINTEXT_" + UUID.randomUUID();
        GitHubOAuthResult result = sample(githubUserId, plaintextToken, "refresh-token");

        ProvisioningOutcome outcome = provisioningService.provisionFromGitHub(result);
        UUID userId = outcome.userId();

        byte[] storedBytes = jdbc.queryForObject(
                "select encrypted_access_token from git_provider_connections where user_id = ?",
                byte[].class, userId);
        assertThat(storedBytes).isNotNull();
        // The plaintext must not appear anywhere in the stored ciphertext.
        String storedAsText = new String(storedBytes, StandardCharsets.ISO_8859_1);
        assertThat(storedAsText).doesNotContain(plaintextToken);

        // The credential store can still recover the original token (round-trip).
        GitProviderConnection connection = connections
                .findByProviderAndProviderAccountIdAndUserId(RepositoryProvider.GITHUB, githubUserId, userId)
                .orElseThrow();
        assertThat(credentialStore.decryptAccessToken(connection.getId())).isEqualTo(plaintextToken);
    }

    @Test
    void refreshTokenIsStoredAsHashNotPlaintext() {
        String githubUserId = "gh-" + UUID.randomUUID();
        GitHubOAuthResult result = sample(githubUserId, "access-token", "refresh-token");

        ProvisioningOutcome outcome = provisioningService.provisionFromGitHub(result);
        String rawRefreshToken = outcome.tokens().refreshToken();
        UUID userId = outcome.userId();

        String storedHash = jdbc.queryForObject(
                "select token_hash from auth_refresh_tokens where user_id = ? order by issued_at desc limit 1",
                String.class, userId);

        assertThat(storedHash).isNotEqualTo(rawRefreshToken);
        assertThat(storedHash).isEqualTo(refreshTokenHasher.hash(rawRefreshToken));
        assertThat(storedHash).hasSize(64); // SHA-256 hex

        Integer rawMatches = jdbc.queryForObject(
                "select count(*) from auth_refresh_tokens where token_hash = ?", Integer.class, rawRefreshToken);
        assertThat(rawMatches).isZero();
    }

    @Test
    void issuesAccessAndRefreshTokens() {
        ProvisioningOutcome outcome =
                provisioningService.provisionFromGitHub(sample("gh-" + UUID.randomUUID(), "a", "r"));
        assertThat(outcome.tokens().accessToken()).isNotBlank();
        assertThat(outcome.tokens().refreshToken()).isNotBlank();
        assertThat(outcome.tokens().accessTokenId()).isNotBlank();
    }

    private Integer countFor(String table, String column, UUID value) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, value);
    }

    private static GitHubOAuthResult sample(String githubUserId, String accessToken, String refreshToken) {
        return new GitHubOAuthResult(
                githubUserId,
                githubUserId + "-login",
                null, // email may be absent
                "Display Name",
                "https://avatar.example/" + githubUserId,
                accessToken,
                refreshToken,
                null,
                "repo,read:user");
    }
}
