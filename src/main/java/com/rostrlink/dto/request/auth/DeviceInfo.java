package com.rostrlink.dto.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Structured device metadata sent by the client on login.
 *
 * <p>For standard (web/mobile) logins this is supplied by the caller.
 * For kiosk logins (PIN / NFC) the fields are derived server-side from
 * {@code kiosk_id} and the authentication method, so the client does
 * not need to send this object.
 *
 * <p>Serialised as JSON and stored in {@code sessions.device_info} and
 * {@code devices.device_info} for audit and device-management purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfo {

    // ── Client-supplied (web / mobile) ────────────────────────────────────

    /**
     * Client platform: {@code "web"}, {@code "ios"}, {@code "android"}.
     * Populated by the front-end; validated loosely (not an enum) to allow
     * future clients without a backend change.
     */
    private String platform;

    /**
     * Raw {@code User-Agent} string or a trimmed application identifier
     * (e.g. {@code "RostrLink-iOS/2.3.1"}).
     */
    private String userAgent;

    /**
     * OS name and version as reported by the client (e.g. {@code "iOS 17.4"},
     * {@code "Android 14"}, {@code "Windows 11"}).
     */
    private String os;

    /**
     * Application version reported by the mobile/web client
     * (e.g. {@code "2.3.1"}). Useful for detecting outdated clients.
     */
    private String appVersion;

    /**
     * Human-readable device model when available
     * (e.g. {@code "iPhone 15 Pro"}, {@code "Samsung Galaxy S24"}).
     * Left {@code null} for web browsers.
     */
    private String deviceModel;

    /**
     * Locale / language code (e.g. {@code "vi-VN"}, {@code "en-US"}).
     * Used for OTP/notification localisation.
     */
    private String locale;

    // ── Server-derived (kiosk) ─────────────────────────────────────────────

    /**
     * Logical kiosk identifier; set server-side for kiosk sessions
     * ({@code session_type = "kiosk"}).  {@code null} for user sessions.
     */
    private String kioskId;

    /**
     * Authentication method used at the kiosk: {@code "pin"} or {@code "nfc"}.
     * Set server-side; {@code null} for user sessions.
     */
    private String kioskAuthMethod;
}