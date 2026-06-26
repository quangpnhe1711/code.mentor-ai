package com.lvn.codementor.ai.github.application;

import java.nio.file.Path;

/**
 * Inputs for a single clone operation. The {@code accessToken} is held only transiently for the
 * duration of the clone and must never be logged or echoed in errors.
 *
 * @param cloneUrl        HTTPS clone URL (e.g. {@code https://github.com/owner/repo.git})
 * @param accessToken     decrypted provider access token (used only as a credential at clone time)
 * @param ref             branch/tag/ref to check out; {@code null} for the default branch
 * @param targetDirectory empty local directory to clone into
 */
public record GitCloneSpec(String cloneUrl, String accessToken, String ref, Path targetDirectory) {
}
