package com.rostrlink.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostrlink.auth.config.AppProperties;
import com.rostrlink.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Only apply rate limiting to auth endpoints */
    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/auth/login",
            "/auth/kiosk/pin-login",
            "/auth/kiosk/nfc-scan",
            "/auth/forgot-password",
            "/auth/verify-otp"
    );

    private static final String RL_IP_PREFIX   = "ratelimit:login:ip:";
    private static final String RL_USER_PREFIX = "ratelimit:login:user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!isRateLimited(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String ipKey = RL_IP_PREFIX + ip;

        int windowSeconds = props.getLogin().getRateLimitWindowSeconds();

        // ── IP-level check ───────────────────────────────────────────────────
        long ipCount = increment(ipKey, windowSeconds);
        if (ipCount > props.getLogin().getRateLimitIpMax()) {
            log.warn("IP rate limit exceeded for {}", ip);
            writeRateLimitResponse(response, "Too many requests from this IP");
            return;
        }

        // ── Identifier-level check (parse identifier from body is expensive;
        //    use IP-only for pre-auth, identifier for post-auth audit) ─────────
        // For simplicity, identifier-level limiting is handled in AuthService
        // via login_attempts table. The Redis counter here is the lightweight
        // first gate per IP.

        chain.doFilter(request, response);
    }

    /**
     * Increments a Redis counter and sets TTL on first increment.
     * Returns the new counter value.
     */
    private long increment(String key, int windowSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // First hit — set expiry
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count != null ? count : 0L;
    }

    private boolean isRateLimited(String path) {
        return RATE_LIMITED_PATHS.stream().anyMatch(path::startsWith);
    }

    private void writeRateLimitResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error("RATE_LIMIT_EXCEEDED", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}