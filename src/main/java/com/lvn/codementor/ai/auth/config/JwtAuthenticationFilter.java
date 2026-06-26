package com.lvn.codementor.ai.auth.config;

import com.lvn.codementor.ai.auth.application.AuthenticatedUser;
import com.lvn.codementor.ai.auth.application.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests bearing a valid platform JWT (ADR-009, doc 14 §6).
 *
 * <p>Reads {@code Authorization: Bearer <accessToken>}, validates it with {@link JwtService}, and on
 * success puts an {@link AuthenticatedUser} principal into the security context. An absent or invalid
 * token leaves the request unauthenticated; protected endpoints are then rejected by the
 * authentication entry point. The GitHub OAuth token is never used for platform authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Jws<Claims> jws = jwtService.parse(token);
                UUID userId = UUID.fromString(jws.getPayload().getSubject());
                AuthenticatedUser principal = new AuthenticatedUser(userId);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Invalid/expired token: leave the request unauthenticated (entry point returns 401).
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
