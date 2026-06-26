package com.lvn.codementor.ai.github.infrastructure;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import com.lvn.codementor.ai.github.application.GitCloneResult;
import com.lvn.codementor.ai.github.application.GitCloneSpec;
import com.lvn.codementor.ai.github.application.port.GitRepositoryCloneClient;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

/**
 * Clones a GitHub repository over HTTPS with JGit (no native git binary required).
 *
 * <p>The access token is supplied to JGit only as a transport credential ({@code x-access-token} /
 * token). It is never logged. On any failure a sanitized {@link ErrorCode#GITHUB_INTEGRATION_ERROR}
 * is thrown — the original JGit message (which can echo the remote URL) is intentionally discarded so
 * no credential or provider detail reaches callers.
 */
@Component
public class JGitRepositoryCloneClient implements GitRepositoryCloneClient {

    @Override
    public GitCloneResult cloneRepository(GitCloneSpec spec) {
        var credentials = new UsernamePasswordCredentialsProvider("x-access-token", spec.accessToken());
        try (Git git = Git.cloneRepository()
                .setURI(spec.cloneUrl())
                .setDirectory(spec.targetDirectory().toFile())
                .setCredentialsProvider(credentials)
                .setBranch(spec.ref()) // null → remote default branch
                .setDepth(1) // shallow: only the tip commit is needed for a snapshot
                .call()) {

            Repository repo = git.getRepository();
            ObjectId head = repo.resolve("HEAD");
            String commitSha = head == null ? null : head.getName();
            String branch = repo.getBranch();
            return new GitCloneResult(commitSha, branch);
        } catch (Exception e) {
            // Discard the original message: it may include the remote URL. Never include the token.
            throw new AppException(ErrorCode.GITHUB_INTEGRATION_ERROR, "Failed to clone repository from GitHub");
        }
    }
}
