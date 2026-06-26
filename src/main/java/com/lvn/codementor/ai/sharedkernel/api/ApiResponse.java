package com.lvn.codementor.ai.sharedkernel.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Standard response envelope (doc 07 §2). Every REST response — success or error — is wrapped in
 * this shape so the frontend has a single contract.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(boolean success, T data, ApiError error, ApiMeta meta) {

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(true, data, null, new ApiMeta(requestId));
    }

    public static <T> ApiResponse<T> error(String code, String message, List<String> details, String requestId) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), new ApiMeta(requestId));
    }
}
