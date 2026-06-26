package com.lvn.codementor.ai.provisioning;

import com.lvn.codementor.ai.identity.User;
import com.lvn.codementor.ai.identity.UserJpaRepository;
import com.lvn.codementor.ai.organization.Organization;
import com.lvn.codementor.ai.organization.OrganizationJpaRepository;
import com.lvn.codementor.ai.organization.OrganizationMember;
import com.lvn.codementor.ai.organization.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.organization.OrganizationRole;
import com.lvn.codementor.ai.organization.OrganizationType;
import com.lvn.codementor.ai.repository.GitProviderCredentialStore;
import com.lvn.codementor.ai.repository.RepositoryProvider;
import com.lvn.codementor.ai.security.IssuedAccessToken;
import com.lvn.codementor.ai.security.IssuedRefreshToken;
import com.lvn.codementor.ai.security.JwtService;
import com.lvn.codementor.ai.security.RefreshTokenService;
import com.lvn.codementor.ai.sharedkernel.error.AppException;
import com.lvn.codementor.ai.sharedkernel.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application coordinator for first-login provisioning (doc 14 §5, ADR-009/ADR-010).
 *
 * <p>Flow: resolve/create user → ensure personal org → ensure owner membership → upsert encrypted
 * provider connection (all in one DB transaction) → after commit, issue platform JWT + refresh
 * token. It calls each module through its port and never reaches through another module's entities.
 *
 * <p>Key guarantees:
 * <ul>
 *   <li>The DB writes are one transaction; an encryption failure mid-flow rolls everything back.</li>
 *   <li>The JWT is issued only after the transaction commits (never for an uncommitted account).</li>
 *   <li>Idempotent on {@code githubUserId}: repeated logins update, never duplicate.</li>
 *   <li>The GitHub OAuth token is never embedded in the platform JWT and is stored encrypted only.</li>
 * </ul>
 */
@Service
public class FirstLoginProvisioningService {

    private final UserJpaRepository users;
    private final OrganizationJpaRepository organizations;
    private final OrganizationMemberJpaRepository members;
    private final GitProviderCredentialStore credentialStore;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TransactionTemplate transactionTemplate;

    public FirstLoginProvisioningService(
            UserJpaRepository users,
            OrganizationJpaRepository organizations,
            OrganizationMemberJpaRepository members,
            GitProviderCredentialStore credentialStore,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.organizations = organizations;
        this.members = members;
        this.credentialStore = credentialStore;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProvisioningOutcome provisionFromGitHub(GitHubOAuthResult result) {
        PersistedAccount account;
        try {
            account = transactionTemplate.execute(status -> persistAccount(result));
        } catch (AppException e) {
            // Preserve meaningful codes (e.g. CREDENTIAL_CRYPTO_ERROR from token encryption).
            throw e;
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.PROVISIONING_FAILED, "Failed to provision account", e);
        }

        // After commit only: issue tokens. The GitHub token is NOT part of the JWT.
        IssuedAccessToken access = jwtService.issue(account.userId());
        IssuedRefreshToken refresh = refreshTokenService.issue(account.userId());

        IssuedTokens tokens = new IssuedTokens(
                access.token(), access.expiresAt(), refresh.rawToken(), refresh.expiresAt(), access.jti());
        return new ProvisioningOutcome(account.userId(), account.organizationId(), tokens);
    }

    /** The transactional DB part. Idempotent: looks up before creating at every step. */
    private PersistedAccount persistAccount(GitHubOAuthResult result) {
        User user = users.findByGithubUserId(result.githubUserId())
                .map(existing -> {
                    existing.updateProfile(
                            result.githubLogin(), result.email(), result.displayName(), result.avatarUrl());
                    return existing;
                })
                .orElseGet(() -> new User(
                        result.githubUserId(),
                        result.githubLogin(),
                        result.email(),
                        result.displayName(),
                        result.avatarUrl()));
        user = users.save(user);
        UUID userId = user.getId();

        Organization personalOrg = organizations
                .findByOwnerUserIdAndType(userId, OrganizationType.PERSONAL)
                .orElseGet(() -> organizations.save(Organization.personalFor(
                        userId, personalOrgName(result), personalOrgSlug(result))));

        members.findByOrganizationIdAndUserId(personalOrg.getId(), userId)
                .orElseGet(() -> members.save(
                        new OrganizationMember(personalOrg.getId(), userId, OrganizationRole.OWNER)));

        // Upsert encrypted credentials. Encryption failure here aborts the whole transaction.
        credentialStore.saveOrUpdate(
                userId,
                RepositoryProvider.GITHUB,
                result.githubLogin(),
                result.githubUserId(),
                result.accessToken(),
                result.refreshToken(),
                result.tokenExpiresAt(),
                result.scopes());

        return new PersistedAccount(userId, personalOrg.getId());
    }

    private static String personalOrgName(GitHubOAuthResult result) {
        String base = result.displayName() != null ? result.displayName() : result.githubLogin();
        return base + " (personal)";
    }

    private static String personalOrgSlug(GitHubOAuthResult result) {
        // Deterministic and globally unique: tied to the immutable GitHub user id.
        return "personal-" + result.githubUserId();
    }

    private record PersistedAccount(UUID userId, UUID organizationId) {
    }
}
