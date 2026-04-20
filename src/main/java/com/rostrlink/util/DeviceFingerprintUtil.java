package com.rostrlink.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class DeviceFingerprintUtil {

    /**
     * Derives a fingerprint from User-Agent + X-Device-Id header.
     * In production, enrich with TLS client hints or a JS-collected fingerprint
     * sent in a custom header (e.g. X-Client-Fingerprint).
     */
    public String extract(HttpServletRequest request) {
        String ua = Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown");
        String deviceId = Optional.ofNullable(request.getHeader("X-Device-Id")).orElse("");
        String clientFp = Optional.ofNullable(request.getHeader("X-Client-Fingerprint")).orElse("");

        String raw = ua + "|" + deviceId + "|" + clientFp;
        return sha256Hex(raw);
    }

    /** For kiosk: the kiosk_id IS the fingerprint */
    public String kioskFingerprint(String kioskId) {
        return sha256Hex("kiosk|" + kioskId);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}