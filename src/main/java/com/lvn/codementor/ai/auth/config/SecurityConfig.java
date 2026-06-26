package com.lvn.codementor.ai.auth.config;

import com.lvn.codementor.ai.auth.application.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security (ADR-009): no server sessions, CSRF disabled (token-based API only).
 *
 * <p>The dev provisioning endpoint is public; everything under {@code /api/**} requires a valid
 * platform JWT (doc 07 §10). Authentication is performed by {@link JwtAuthenticationFilter}; an
 * unauthenticated hit on a protected endpoint is rejected by {@link RestAuthenticationEntryPoint}
 * with the standard {@code UNAUTHENTICATED} envelope.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, RestAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/github/login", "/api/auth/github/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/github/provision/dev").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
