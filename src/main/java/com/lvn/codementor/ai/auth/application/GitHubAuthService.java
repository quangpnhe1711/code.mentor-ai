package com.lvn.codementor.ai.auth.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitHubTokenResult;
import com.lvn.codementor.ai.github.application.GitHubUserProfile;
import com.lvn.codementor.ai.github.application.port.GitHubOAuthClient;
import com.lvn.codementor.ai.github.application.port.GitHubUserClient;
import com.lvn.codementor.ai.github.config.GitHubProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the real GitHub OAuth web flow on top of the existing provisioning service.
 *
 * <ul>
 *   <li>{@link #buildAuthorizationUrl()} — issue a CSRF state and build the GitHub authorize URL.</li>
 *   <li>{@link #handleCallback(String, String)} — validate state, exchange code, fetch profile,
 *       provision the platform account, and return the issued platform tokens.</li>
 * </ul>
 *
 * The GitHub access token obtained here is handed to provisioning (which encrypts it) and is never
 * returned to the caller or put into the platform JWT.
 */
@Service
public class GitHubAuthService {

    private final GitHubProperties properties;
    private final OAuthStateStore stateStore;
    private final GitHubOAuthClient oauthClient;
    private final GitHubUserClient userClient;
    private final FirstLoginProvisioningService provisioningService;

    public GitHubAuthService(
            GitHubProperties properties,
            OAuthStateStore stateStore,
            GitHubOAuthClient oauthClient,
            GitHubUserClient userClient,
            FirstLoginProvisioningService provisioningService) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.oauthClient = oauthClient;
        this.userClient = userClient;
        this.provisioningService = provisioningService;
    }

    /** Build the GitHub authorization URL with a fresh, stored CSRF state. */
    public String buildAuthorizationUrl() {
        String state = stateStore.issue();
        String scope = properties.scopes() == null ? "" : properties.scopes().replace(',', ' ');
        return properties.webBaseUrl() + "/login/oauth/authorize"
                + "?client_id=" + enc(properties.clientId())
                + "&redirect_uri=" + enc(properties.redirectUri())
                + "&scope=" + enc(scope)
                + "&state=" + enc(state);
    }

    /** Validate state, complete the exchange, and provision the platform account. */
    public ProvisioningOutcome handleCallback(String code, String state) {
        if (!stateStore.consume(state)) {
            throw new AppException(ErrorCode.GITHUB_OAUTH_STATE_INVALID, "Invalid or expired OAuth state");
        }
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "Missing OAuth code");
        }

        GitHubTokenResult token = oauthClient.exchangeCodeForToken(code, properties.redirectUri());
        GitHubUserProfile profile = userClient.getCurrentUser(token.accessToken());

        GitHubOAuthResult result = new GitHubOAuthResult(
                profile.id(),
                profile.login(),
                profile.email(),
                profile.name(),
                profile.avatarUrl(),
                token.accessToken(),
                token.refreshToken(),
                token.expiresAt(),
                token.scope());

        return provisioningService.provisionFromGitHub(result);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
