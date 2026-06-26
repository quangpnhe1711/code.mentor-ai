package com.lvn.codementor.ai.github.application.port;

import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.github.application.GitCloneSpec;

/**
 * Clones a Git repository into a local directory at the provider boundary. Implementations use the
 * spec's access token only as a transport credential and must never log it or include it (or the
 * provider's response body) in exception messages.
 */
public interface GitRepositoryCloneClient {

    GitCloneResult cloneRepository(GitCloneSpec spec);
}
