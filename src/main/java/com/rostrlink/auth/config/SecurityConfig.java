package com.rostrlink.auth.config;

import com.rostrlink.filter.AuthenticationFilter;
import com.rostrlink.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/kiosk/pin-login",
            "/auth/kiosk/nfc-scan",
            "/auth/forgot-password",
            "/auth/verify-otp",
            "/auth/reset-password",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled — we protect with SameSite cookie + custom CSRF header
                .csrf(csrf -> csrf.disable())
                // No Spring-managed sessions; our Redis sessions are managed manually
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                // RateLimit runs first, then our session-based auth filter
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt with strength 12.
     * If you switch to Argon2id, replace with a Spring Security Argon2PasswordEncoder
     * or your own BouncyCastle-backed implementation in PasswordUtil.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}