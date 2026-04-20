package com.rostrlink.service.impl.auth;

import com.rostrlink.auth.config.AppProperties;
import com.rostrlink.dto.request.auth.ForgotPasswordRequest;
import com.rostrlink.dto.request.auth.KioskPinLoginRequest;
import com.rostrlink.dto.request.auth.LoginRequest;
import com.rostrlink.dto.request.auth.NfcScanRequest;
import com.rostrlink.dto.request.auth.ResetPasswordRequest;
import com.rostrlink.dto.request.auth.VerifyOtpRequest;
import com.rostrlink.dto.response.ApiResponse;
import com.rostrlink.dto.response.LoginResponse;
import com.rostrlink.entity.auth.Device;
import com.rostrlink.entity.auth.LoginAttempt;
import com.rostrlink.entity.auth.NfcTag;
import com.rostrlink.entity.auth.Session;
import com.rostrlink.entity.auth.User;
import com.rostrlink.exception.auth.AccountLockedException;
import com.rostrlink.exception.auth.InvalidCredentialsException;
import com.rostrlink.exception.auth.UserNotFoundException;
import com.rostrlink.redis.RedisSession;
import com.rostrlink.repository.UserRepository;
import com.rostrlink.repository.auth.DeviceRepository;
import com.rostrlink.repository.auth.LoginAttemptRepository;
import com.rostrlink.repository.auth.NfcTagRepository;
import com.rostrlink.repository.auth.SessionRepository;
import com.rostrlink.service.AuthService;
import com.rostrlink.util.CookieUtil;
import com.rostrlink.util.DeviceFingerprintUtil;
import com.rostrlink.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final DeviceRepository deviceRepository;
    private final NfcTagRepository nfcTagRepository;

    private final SessionService sessionService;
    private final PermissionService permissionService;
    private final OtpService otpService;

    private final PasswordUtil passwordUtil;
    private final DeviceFingerprintUtil deviceFingerprintUtil;
    private final CookieUtil cookieUtil;
    private final AppProperties props;

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest req,
                                                            HttpServletRequest httpReq,
                                                            HttpServletResponse httpRes) {
        String ip = getClientIp(httpReq);
        String fingerprint = deviceFingerprintUtil.extract(httpReq);

        User user = userRepository.findByEmail(req.getIdentifier())
                .orElseGet(() -> {
                    recordAttempt(null, req.getIdentifier(), ip, fingerprint, false);
                    throw new InvalidCredentialsException();
                });

        if (!"active".equals(user.getStatus())) {
            throw new AccountLockedException("Account is not active");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new AccountLockedException("Account is locked until " + user.getLockedUntil());
        }

        if (!passwordUtil.verify(req.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user, ip, fingerprint);
            throw new InvalidCredentialsException();
        }

        resetFailedAttempts(user);
        recordAttempt(user, req.getIdentifier(), ip, fingerprint, true);
        upsertDevice(user, fingerprint, req.getDeviceInfo());

        List<String> roleNames = permissionService.getRoleNamesForUser(user.getUserId());
        Map<String, Object> scope = permissionService.getUserScope(user.getUserId());
        int permVersion = sessionService.getCurrentPermissionsVersion(user.getUserId());

        Instant now = Instant.now();
        Instant expires = now.plusSeconds(props.getSession().getTtlSeconds());

        RedisSession redisSession = RedisSession.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .roles(roleNames)
                .sessionType("user")
                .deviceFingerprint(fingerprint)
                .ipAddress(ip)
                .permissionsVersion(permVersion)
                .createdAt(now)
                .expiresAt(expires)
                .scope(scope)
                .build();

        String sessionId = sessionService.create(redisSession);
        persistAuditSession(sessionId, user, "user", req.getDeviceInfo(), fingerprint, ip, expires, permVersion);
        enforceConcurrentSessionLimit(user.getUserId());
        cookieUtil.writeSessionCookie(httpRes, sessionId, (int) props.getSession().getTtlSeconds());

        LoginResponse resp = buildLoginResponse(user, roleNames, scope, "user", expires);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", resp));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> kioskPinLogin(KioskPinLoginRequest req,
                                                                    HttpServletRequest httpReq,
                                                                    HttpServletResponse httpRes) {
        String ip = getClientIp(httpReq);
        String fingerprint = deviceFingerprintUtil.kioskFingerprint(req.getKioskId());

        User kiosk = userRepository.findByEmail(req.getKioskId() + "@kiosk.internal")
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordUtil.verify(req.getPin(), kiosk.getPasswordHash())) {
            handleFailedAttempt(kiosk, ip, fingerprint);
            throw new InvalidCredentialsException();
        }

        resetFailedAttempts(kiosk);

        Instant now = Instant.now();
        Instant expires = now.plusSeconds(props.getSession().getKioskTtlSeconds());

        RedisSession redisSession = RedisSession.builder()
                .userId(kiosk.getUserId())
                .email(kiosk.getEmail())
                .roles(List.of("Kiosk"))
                .sessionType("kiosk")
                .deviceFingerprint(fingerprint)
                .ipAddress(ip)
                .permissionsVersion(0)
                .createdAt(now)
                .expiresAt(expires)
                .scope(Map.of("kiosk_id", req.getKioskId()))
                .build();

        String sessionId = sessionService.create(redisSession);
        persistAuditSession(sessionId, kiosk, "kiosk", req.getKioskId(), fingerprint, ip, expires, 0);
        cookieUtil.writeSessionCookie(httpRes, sessionId, (int) props.getSession().getKioskTtlSeconds());

        LoginResponse resp = buildLoginResponse(kiosk, List.of("Kiosk"),
                Map.of("kiosk_id", req.getKioskId()), "kiosk", expires);
        return ResponseEntity.ok(ApiResponse.ok("Kiosk login successful", resp));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> nfcScan(NfcScanRequest req,
                                                              HttpServletRequest httpReq,
                                                              HttpServletResponse httpRes) {
        String ip = getClientIp(httpReq);
        String fingerprint = deviceFingerprintUtil.kioskFingerprint(req.getKioskId());

        NfcTag tag = nfcTagRepository.findByTagCodeAndRevokedAtIsNull(req.getTagCode())
                .orElseThrow(InvalidCredentialsException::new);

        User user = tag.getUser();
        if (!"active".equals(user.getStatus())) {
            throw new AccountLockedException("Account is not active");
        }

        List<String> roleNames = permissionService.getRoleNamesForUser(user.getUserId());
        Map<String, Object> scope = permissionService.getUserScope(user.getUserId());

        Instant now = Instant.now();
        Instant expires = now.plusSeconds(props.getSession().getKioskTtlSeconds());

        RedisSession redisSession = RedisSession.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .roles(roleNames)
                .sessionType("kiosk")
                .deviceFingerprint(fingerprint)
                .ipAddress(ip)
                .permissionsVersion(0)
                .createdAt(now)
                .expiresAt(expires)
                .scope(scope)
                .build();

        String sessionId = sessionService.create(redisSession);
        persistAuditSession(sessionId, user, "kiosk", req.getKioskId(), fingerprint, ip, expires, 0);
        cookieUtil.writeSessionCookie(httpRes, sessionId, (int) props.getSession().getKioskTtlSeconds());

        LoginResponse resp = buildLoginResponse(user, roleNames, scope, "kiosk", expires);
        return ResponseEntity.ok(ApiResponse.ok("NFC scan successful", resp));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpReq,
                                                    HttpServletResponse httpRes) {
        Optional<String> sidOpt = cookieUtil.read(httpReq, CookieUtil.SESSION_COOKIE);
        if (sidOpt.isPresent()) {
            String sid = sidOpt.get();
            sessionService.get(sid).ifPresent(s -> {
                sessionService.revoke(sid, s.getUserId());
                try {
                    UUID uuid = UUID.fromString(sid);
                    sessionRepository.findById(uuid).ifPresent(dbSession -> {
                        dbSession.setRevokedAt(OffsetDateTime.now());
                        sessionRepository.save(dbSession);
                    });
                } catch (IllegalArgumentException ignored) {
                }
            });
            cookieUtil.clearSessionCookie(httpRes);
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> forgotPassword(ForgotPasswordRequest req) {
        try {
            otpService.sendOtp(req.getEmail(), "password_reset", req.getChannel());
        } catch (Exception e) {
            log.warn("Forgot-password OTP send failed for {}: {}", req.getEmail(), e.getMessage());
        }
        return ResponseEntity.ok(ApiResponse.ok("If this email is registered, you will receive a code.", null));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> verifyOtp(VerifyOtpRequest req) {
        otpService.verifyOtp(req.getEmail(), req.getOtp(), req.getPurpose());
        return ResponseEntity.ok(ApiResponse.ok("OTP verified", null));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<Void>> resetPassword(ResetPasswordRequest req) {
        otpService.verifyOtp(req.getEmail(), req.getOtp(), "password_reset");

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new UserNotFoundException(req.getEmail()));

        user.setPasswordHash(passwordUtil.hash(req.getNewPassword()));
        user.setFailedAttemptCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        sessionService.revokeAllForUser(user.getUserId());
        sessionRepository.revokeAllForUser(user.getUserId(), OffsetDateTime.now(), user.getUserId());

        log.info("Password reset for user {}", user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful. Please log in again.", null));
    }

    private void handleFailedAttempt(User user, String ip, String fingerprint) {
        user.setFailedAttemptCount(user.getFailedAttemptCount() + 1);
        if (user.getFailedAttemptCount() >= props.getLogin().getMaxAttempts()) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(props.getLogin().getLockoutMinutes()));
            log.warn("User {} locked until {}", user.getUserId(), user.getLockedUntil());
        }
        userRepository.save(user);
        recordAttempt(user, user.getEmail(), ip, fingerprint, false);
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedAttemptCount() > 0) {
            user.setFailedAttemptCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private void recordAttempt(User user, String identifier, String ip,
                               String fingerprint, boolean success) {
        LoginAttempt attempt = LoginAttempt.builder()
                .user(user)
                .identifier(identifier)
                .ipAddress(ip)
                .deviceFingerprint(fingerprint)
                .success(success)
                .build();
        loginAttemptRepository.save(attempt);
    }

    private void upsertDevice(User user, String fingerprint, String deviceInfo) {
        deviceRepository.findByUser_UserIdAndDeviceFingerprint(user.getUserId(), fingerprint)
                .ifPresentOrElse(d -> {
                    d.setLastSeenAt(OffsetDateTime.now());
                    deviceRepository.save(d);
                }, () -> {
                    Device d = Device.builder()
                            .user(user)
                            .deviceFingerprint(fingerprint)
                            .deviceInfo(deviceInfo)
                            .build();
                    deviceRepository.save(d);
                });
    }

    private void persistAuditSession(String sessionId, User user, String type,
                                     String deviceInfo, String fingerprint,
                                     String ip, Instant expiresAt, int permVersion) {
        Session s = Session.builder()
                .sessionId(UUID.fromString(sessionId))
                .user(user)
                .sessionType(type)
                .deviceInfo(deviceInfo)
                .deviceFingerprint(fingerprint)
                .ipAddress(ip)
                .expiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .permissionsVersion(permVersion)
                .build();
        sessionRepository.save(s);
    }

    private void enforceConcurrentSessionLimit(Long userId) {
        int max = props.getSession().getMaxConcurrent();
        if (max <= 0) return;

        Set<Object> sessionIds = sessionService.getSessionIdsForUser(userId);
        if (sessionIds != null && sessionIds.size() > max) {
            List<Session> active = sessionRepository.findByUser_UserIdAndRevokedAtIsNull(userId);
            active.sort(Comparator.comparing(Session::getCreatedAt));
            int excess = active.size() - max;
            for (int i = 0; i < excess; i++) {
                Session old = active.get(i);
                sessionService.revoke(old.getSessionId().toString(), userId);
                old.setRevokedAt(OffsetDateTime.now());
                sessionRepository.save(old);
            }
        }
    }

    private LoginResponse buildLoginResponse(User user, List<String> roles,
                                             Map<String, Object> scope,
                                             String sessionType, Instant expiresAt) {
        LoginResponse r = new LoginResponse();
        r.setUserId(user.getUserId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setRoles(roles);
        r.setScope(scope);
        r.setSessionType(sessionType);
        r.setExpiresAt(expiresAt);
        return r;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}