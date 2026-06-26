package com.lvn.codementor.ai.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.lvn.codementor.ai.crypto.CryptoException;
import com.lvn.codementor.ai.crypto.TokenEncryptor;
import com.lvn.codementor.ai.identity.UserJpaRepository;
import com.lvn.codementor.ai.security.AuthRefreshTokenJpaRepository;
import com.lvn.codementor.ai.sharedkernel.error.AppException;
import com.lvn.codementor.ai.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that a token-encryption failure during provisioning rolls back the whole DB transaction
 * and that no platform token is issued (doc 14 §5.4).
 */
class ProvisioningEncryptionFailureIT extends AbstractPostgresIT {

    @MockitoBean
    TokenEncryptor tokenEncryptor;

    @Autowired
    FirstLoginProvisioningService provisioningService;

    @Autowired
    UserJpaRepository users;

    @Autowired
    AuthRefreshTokenJpaRepository refreshTokens;

    @Test
    void encryptionFailureRollsBackProvisioningAndIssuesNoToken() {
        given(tokenEncryptor.encrypt(anyString())).willThrow(new CryptoException("encryption unavailable"));

        String githubUserId = "gh-" + UUID.randomUUID();
        long refreshTokensBefore = refreshTokens.count();

        GitHubOAuthResult result = new GitHubOAuthResult(
                githubUserId, githubUserId + "-login", null, "Name", null, "access", "refresh", null, "repo");

        assertThatThrownBy(() -> provisioningService.provisionFromGitHub(result))
                .isInstanceOf(AppException.class);

        // The user created earlier in the flow must have been rolled back...
        assertThat(users.findByGithubUserId(githubUserId)).isEmpty();
        // ...and no refresh token issued (token issuance only happens after a successful commit).
        assertThat(refreshTokens.count()).isEqualTo(refreshTokensBefore);
    }
}
