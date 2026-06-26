package com.lvn.codementor.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.repository.persistence.ImportedRepositoryJpaRepository;
import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryStatus;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import com.lvn.codementor.ai.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the Foundation migration applied and that its constraints behave as designed (doc 14 §4).
 * Constraint checks use raw SQL so they exercise the DB schema directly, independent of JPA.
 */
@Transactional
class FoundationSchemaIT extends AbstractPostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ImportedRepositoryJpaRepository repositories;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void migrationApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version = '1'",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateGithubUserId() {
        insertUser("dup-github-id");
        assertThatThrownBy(() -> insertUser("dup-github-id"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSecondPersonalOrgForSameOwner() {
        UUID owner = insertUser("owner-1");
        insertOrg(owner, "PERSONAL", "slug-personal-a");
        // Different slug isolates the violation to the one-personal-org-per-owner partial unique index.
        assertThatThrownBy(() -> insertOrg(owner, "PERSONAL", "slug-personal-b"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateMembership() {
        UUID user = insertUser("member-user");
        UUID org = insertOrg(user, "TEAM", "team-slug-1");
        insertMember(org, user, "OWNER");
        assertThatThrownBy(() -> insertMember(org, user, "ADMIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateRepositoryWithinSameOrganization() {
        UUID user = insertUser("repo-user");
        UUID org = insertOrg(user, "PERSONAL", "repo-org-slug");
        UUID conn = insertConnection(user, "repo-acct");
        insertRepo(org, conn, user, "ext-repo-1");
        assertThatThrownBy(() -> insertRepo(org, conn, user, "ext-repo-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateProviderConnection() {
        UUID user = insertUser("conn-user");
        insertConnection(user, "acct-dup");
        assertThatThrownBy(() -> insertConnection(user, "acct-dup"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameRepositoryInDifferentOrganizations() {
        UUID user = insertUser("multi-org-user");
        UUID personalOrg = insertOrg(user, "PERSONAL", "multi-personal");
        UUID teamOrg = insertOrg(user, "TEAM", "multi-team");
        UUID conn = insertConnection(user, "multi-acct");

        insertRepo(personalOrg, conn, user, "shared-ext-id");
        // Same upstream repo (external_repo_id) into a different org must be allowed (BR-REP-002 is per-org).
        assertThatCode(() -> insertRepo(teamOrg, conn, user, "shared-ext-id")).doesNotThrowAnyException();
    }

    @Test
    void persistsErrorReasonForFailedRepository() {
        UUID user = insertUser("err-user");
        UUID org = insertOrg(user, "PERSONAL", "err-org");
        UUID conn = insertConnection(user, "err-acct");

        ImportedRepository repo = new ImportedRepository(
                org, conn, RepositoryProvider.GITHUB, "err-ext", "owner", "repo", "owner/repo",
                RepositoryVisibility.PRIVATE, user);
        repo.markFailed(RepositoryStatus.FAILED, "clone failed: authentication error");
        UUID repoId = repositories.saveAndFlush(repo).getId();

        // Reload from the database to confirm the column round-trips.
        entityManager.clear();
        ImportedRepository reloaded = repositories.findById(repoId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RepositoryStatus.FAILED);
        assertThat(reloaded.getErrorReason()).isEqualTo("clone failed: authentication error");
    }

    // --- raw-SQL helpers (exercise DB constraints directly) ---

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private UUID insertUser(String githubUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into users (id, github_user_id, github_login, created_at, updated_at) values (?,?,?,?,?)",
                id, githubUserId, githubUserId + "-login", now(), now());
        return id;
    }

    private UUID insertOrg(UUID ownerUserId, String type, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into organizations (id, name, slug, type, owner_user_id, created_at, updated_at)"
                        + " values (?,?,?,?,?,?,?)",
                id, "Org " + slug, slug, type, ownerUserId, now(), now());
        return id;
    }

    private UUID insertMember(UUID organizationId, UUID userId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into organization_members (id, organization_id, user_id, role, created_at, updated_at)"
                        + " values (?,?,?,?,?,?)",
                id, organizationId, userId, role, now(), now());
        return id;
    }

    private UUID insertConnection(UUID userId, String accountId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into git_provider_connections (id, provider, user_id, provider_account_login,"
                        + " provider_account_id, encrypted_access_token, key_version, encryption_algorithm,"
                        + " status, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?)",
                id, "GITHUB", userId, accountId + "-login", accountId, new byte[] {1, 2, 3},
                "v1", "AES_GCM", "CONNECTED", now(), now());
        return id;
    }

    private UUID insertRepo(UUID organizationId, UUID connectionId, UUID userId, String externalRepoId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into repositories (id, organization_id, git_provider_connection_id, provider,"
                        + " external_repo_id, owner_login, name, full_name, visibility, status,"
                        + " imported_by_user_id, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, organizationId, connectionId, "GITHUB", externalRepoId, "owner", "repo", "owner/repo",
                "PUBLIC", "ACTIVE", userId, now(), now());
        return id;
    }
}
