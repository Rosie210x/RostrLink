package com.rostrlink.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostrlink.dto.response.auth.ApiResponse;
import com.rostrlink.redis.RedisSession;
import com.rostrlink.service.impl.auth.SessionService;
import com.rostrlink.util.CookieUtil;
import com.rostrlink.util.DeviceFingerprintUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    /** Paths that never require a session */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/auth/login",
            "/auth/kiosk/",
            "/auth/forgot-password",
            "/auth/verify-otp",
            "/auth/reset-password",
            "/actuator/health"
    );

    private final SessionService sessionService;
    private final CookieUtil cookieUtil;
    private final DeviceFingerprintUtil deviceFingerprintUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<String> sidOpt = cookieUtil.read(request, CookieUtil.SESSION_COOKIE);
        if (sidOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "SESSION_MISSING", "Authentication required");
            return;
        }

        String sessionId = sidOpt.get();
        Optional<RedisSession> sessionOpt = sessionService.get(sessionId);

        if (sessionOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Session not found or expired");
            return;
        }

        RedisSession session = sessionOpt.get();

        // 1. Expiry check (belt-and-suspenders; Redis TTL should already evict it)
        if (session.isExpired()) {
            sessionService.revoke(sessionId, session.getUserId());
            writeError(response, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session has expired");
            return;
        }

        // 2. Device fingerprint binding
        String incoming = deviceFingerprintUtil.extract(request);
        if (!incoming.equals(session.getDeviceFingerprint())) {
            log.warn("Device mismatch for session {} user {}", sessionId, session.getUserId());
            writeError(response, HttpStatus.FORBIDDEN, "DEVICE_MISMATCH", "Device mismatch");
            return;
        }

        // 3. Permissions version check — re-evaluate if roles have changed
        int currentVersion = sessionService.getCurrentPermissionsVersion(session.getUserId());
        if (session.getPermissionsVersion() < currentVersion) {
            // Revoke stale session; client must re-login to get fresh permissions
            sessionService.revoke(sessionId, session.getUserId());
            writeError(response, HttpStatus.UNAUTHORIZED, "PERMISSIONS_CHANGED",
                    "Your permissions have changed. Please log in again.");
            return;
        }

        // 4. Build Spring SecurityContext
        List<SimpleGrantedAuthority> authorities = session.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(session.getUserId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 5. Attach session to request for downstream use
        request.setAttribute("redisSession", session);
        request.setAttribute("sessionId", sessionId);

        // 6. Sliding expiration: refresh TTL on each use
        sessionService.touch(sessionId, session);

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}