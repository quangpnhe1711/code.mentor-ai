package com.lvn.codementor.ai.github.application;

/**
 * Minimal GitHub user profile used to provision/refresh the platform account.
 *
 * @param id        stable numeric GitHub user id, as a string (join key → {@code users.github_user_id})
 * @param login     GitHub username
 * @param email     primary email; may be {@code null}
 * @param name      display name; may be {@code null}
 * @param avatarUrl avatar URL; may be {@code null}
 */
public record GitHubUserProfile(String id, String login, String email, String name, String avatarUrl) {
}
