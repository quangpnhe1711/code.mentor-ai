package com.lvn.codementor.ai.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security baseline (ADR-009): no server sessions, CSRF disabled (token-based API).
 *
 * <p>Foundation has no protected REST endpoints yet, so requests are permitted to keep the context
 * valid and avoid the default login page. The JWT authentication filter (validating the platform
 * token via {@link JwtService}) and per-endpoint authorization — "all endpoints except the OAuth
 * flow require auth" (doc 07 §10) — are added in the next slice when controllers are introduced.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
