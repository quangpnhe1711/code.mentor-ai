package com.lvn.codementor.ai.identity;

import com.lvn.codementor.ai.sharedkernel.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An authenticated person (doc 14 §3.1). Identified by the stable {@code githubUserId} (not the
 * mutable login). Email may be absent from the GitHub profile.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "github_user_id", nullable = false, updatable = false)
    private String githubUserId;

    @Column(name = "github_login", nullable = false)
    private String githubLogin;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // for JPA
    }

    public User(String githubUserId, String githubLogin, String email, String displayName, String avatarUrl) {
        this.githubUserId = githubUserId;
        this.githubLogin = githubLogin;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    /** Refresh mutable profile fields on a subsequent login. */
    public void updateProfile(String githubLogin, String email, String displayName, String avatarUrl) {
        this.githubLogin = githubLogin;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
