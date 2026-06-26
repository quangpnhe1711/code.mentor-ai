package com.lvn.codementor.ai.repository.api;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.repository.api.request.CreateRepositorySnapshotRequest;
import com.lvn.codementor.ai.repository.api.response.RepositoryFileEntryResponse;
import com.lvn.codementor.ai.repository.api.response.RepositorySnapshotResponse;
import com.lvn.codementor.ai.repository.application.RepositorySnapshotService;
import com.lvn.codementor.ai.repository.application.command.RepositorySnapshotCommand;
import com.lvn.codementor.ai.repository.domain.RepositorySnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repository snapshot preparation under an organization's repository. Requires a platform JWT
 * (SecurityConfig) and organization membership (enforced in the service). Responses never include the
 * local workspace path or any credential data.
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories/{repositoryId}/snapshots")
public class RepositorySnapshotController {

    private final CurrentUser currentUser;
    private final RepositorySnapshotService snapshotService;

    public RepositorySnapshotController(CurrentUser currentUser, RepositorySnapshotService snapshotService) {
        this.currentUser = currentUser;
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RepositorySnapshotResponse>> createSnapshot(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @RequestBody(required = false) CreateRepositorySnapshotRequest request) {
        UUID userId = currentUser.requireUserId();
        String sourceRef = request == null ? null : request.sourceRef();
        RepositorySnapshot snapshot = snapshotService.createSnapshot(
                userId, organizationId, repositoryId, new RepositorySnapshotCommand(sourceRef));
        ApiResponse<RepositorySnapshotResponse> body =
                ApiResponse.ok(RepositorySnapshotResponse.from(snapshot), requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{snapshotId}/files")
    public ApiResponse<List<RepositoryFileEntryResponse>> listFiles(
            @PathVariable UUID organizationId,
            @PathVariable UUID repositoryId,
            @PathVariable UUID snapshotId) {
        UUID userId = currentUser.requireUserId();
        List<RepositoryFileEntryResponse> data =
                snapshotService.listFiles(userId, organizationId, repositoryId, snapshotId).stream()
                        .map(RepositoryFileEntryResponse::from)
                        .toList();
        return ApiResponse.ok(data, requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
