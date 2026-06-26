package com.lvn.codementor.ai.github.application;

/**
 * A repository as seen on GitHub for the connected account. Carries no credential data.
 *
 * @param externalRepoId stable GitHub repository id, as a string
 * @param ownerLogin     owner login
 * @param name           short name
 * @param fullName       {@code owner/name}
 * @param visibility     {@code PUBLIC} or {@code PRIVATE} (derived from GitHub's flags)
 * @param defaultBranch  default branch; may be {@code null}
 * @param htmlUrl        browser URL
 * @param isPrivate      whether the repository is private
 */
public record GitHubRepositorySummary(
        String externalRepoId,
        String ownerLogin,
        String name,
        String fullName,
        String visibility,
        String defaultBranch,
        String htmlUrl,
        boolean isPrivate) {
}
