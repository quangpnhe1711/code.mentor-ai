package com.lvn.codementor.ai.repository.api.request;

import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for importing a repository into an organization. */
public record ImportRepositoryRequest(
        @NotNull RepositoryProvider provider,
        @NotBlank String externalRepoId,
        @NotBlank String ownerLogin,
        @NotBlank String name,
        @NotBlank String fullName,
        @NotNull RepositoryVisibility visibility,
        String defaultBranch) {
}
