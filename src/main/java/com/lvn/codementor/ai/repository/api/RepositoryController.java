package com.lvn.codementor.ai.repository.api;
import com.lvn.codementor.ai.repository.api.response.RepositoryResponse;
import com.lvn.codementor.ai.repository.api.request.ImportRepositoryRequest;

import com.lvn.codementor.ai.auth.application.CurrentUser;
import com.lvn.codementor.ai.repository.application.command.RepositoryImportCommand;
import com.lvn.codementor.ai.repository.application.RepositoryImportService;
import com.lvn.codementor.ai.repository.domain.ImportedRepository;
import com.lvn.codementor.ai.common.api.ApiResponse;
import jakarta.validation.Valid;
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
 * Repository import/listing/detail under an organization (FR-003/FR-005 area). All endpoints require
 * a valid platform JWT (SecurityConfig) and organization membership (enforced in the service). No
 * credential data is ever returned (responses use {@link RepositoryResponse}).
 */
@RestController
@RequestMapping("/api/organizations/{organizationId}/repositories")
public class RepositoryController {

    private final CurrentUser currentUser;
    private final RepositoryImportService repositoryImportService;

    public RepositoryController(CurrentUser currentUser, RepositoryImportService repositoryImportService) {
        this.currentUser = currentUser;
        this.repositoryImportService = repositoryImportService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RepositoryResponse>> importRepository(
            @PathVariable UUID organizationId, @Valid @RequestBody ImportRepositoryRequest request) {
        UUID userId = currentUser.requireUserId();
        RepositoryImportCommand command = new RepositoryImportCommand(
                request.provider(),
                request.externalRepoId(),
                request.ownerLogin(),
                request.name(),
                request.fullName(),
                request.visibility(),
                request.defaultBranch());
        ImportedRepository imported = repositoryImportService.importRepository(userId, organizationId, command);
        ApiResponse<RepositoryResponse> body = ApiResponse.ok(RepositoryResponse.from(imported), requestId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public ApiResponse<List<RepositoryResponse>> listRepositories(@PathVariable UUID organizationId) {
        UUID userId = currentUser.requireUserId();
        List<RepositoryResponse> data = repositoryImportService.listByOrganization(userId, organizationId).stream()
                .map(RepositoryResponse::from)
                .toList();
        return ApiResponse.ok(data, requestId());
    }

    @GetMapping("/{repositoryId}")
    public ApiResponse<RepositoryResponse> getRepository(
            @PathVariable UUID organizationId, @PathVariable UUID repositoryId) {
        UUID userId = currentUser.requireUserId();
        ImportedRepository repository = repositoryImportService.getByOrganization(userId, organizationId, repositoryId);
        return ApiResponse.ok(RepositoryResponse.from(repository), requestId());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
