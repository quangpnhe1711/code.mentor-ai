package com.lvn.codementor.ai.repository.application.command;

import com.lvn.codementor.ai.repository.domain.RepositoryProvider;
import com.lvn.codementor.ai.repository.domain.RepositoryVisibility;

/** Inputs for importing a repository into an organization (web layer maps its request to this). */
public record RepositoryImportCommand(
        RepositoryProvider provider,
        String externalRepoId,
        String ownerLogin,
        String name,
        String fullName,
        RepositoryVisibility visibility,
        String defaultBranch) {
}
