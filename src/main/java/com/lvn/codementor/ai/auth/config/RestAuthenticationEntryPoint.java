package com.lvn.codementor.ai.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvn.codementor.ai.common.api.ApiResponse;
import com.lvn.codementor.ai.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns the standard API error envelope with {@code UNAUTHENTICATED} (401) when an unauthenticated
 * request hits a protected endpoint (doc 07 §2, doc 08). Keeps the API stateless — no login redirect.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.UNAUTHENTICATED.name(), "Authentication required", List.of(), UUID.randomUUID().toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
