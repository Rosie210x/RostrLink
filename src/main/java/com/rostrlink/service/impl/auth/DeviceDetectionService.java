package com.rostrlink.service.impl.auth;

import com.rostrlink.dto.request.auth.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Derives a structured {@link DeviceInfo} from the inbound HTTP request.
 *
 * <p>This replaces the previous pattern where callers either passed a raw
 * {@code String} or relied on the client to supply every field unvalidated.
 * The service parses the {@code User-Agent}, {@code X-Device-Id},
 * {@code Accept-Language} and optional custom headers to build a best-effort
 * description of the calling device — entirely server-side, with no client
 * cooperation required.
 *
 * <p><b>Merge strategy</b>: When a client also sends a {@code DeviceInfo}
 * object in the request body (web / mobile logins), call
 * {@link #merge(DeviceInfo, DeviceInfo)} to let client-supplied values win
 * over server-detected ones for fields the client knows better (e.g.,
 * {@code appVersion}, {@code deviceModel}), while server-derived values fill
 * any gaps the client left {@code null}.
 */
@Service
public class DeviceDetectionService {

    // ── Custom headers that richer clients may send ─────────────────────────
    private static final String HEADER_DEVICE_ID    = "X-Device-Id";
    private static final String HEADER_APP_VERSION  = "X-App-Version";
    private static final String HEADER_PLATFORM     = "X-Platform";
    private static final String HEADER_DEVICE_MODEL = "X-Device-Model";

    /**
     * Build a {@link DeviceInfo} entirely from the HTTP request.
     *
     * @param request the inbound servlet request
     * @return a best-effort {@link DeviceInfo}; never {@code null}
     */
    public DeviceInfo detect(HttpServletRequest request) {
        String ua         = header(request, "User-Agent");
        String acceptLang = header(request, "Accept-Language");
        String platform   = header(request, HEADER_PLATFORM);
        String appVersion = header(request, HEADER_APP_VERSION);
        String model      = header(request, HEADER_DEVICE_MODEL);

        // Derive platform from UA when the custom header is absent
        if (platform == null) {
            platform = detectPlatform(ua);
        }

        return DeviceInfo.builder()
                .platform(platform)
                .userAgent(ua)
                .os(detectOs(ua))
                .appVersion(appVersion)
                .deviceModel(model)
                .locale(primaryLocale(acceptLang))
                .build();
    }

    /**
     * Merge client-supplied and server-detected device info.
     *
     * <p>Client values take priority; server values are used only when the
     * corresponding client field is {@code null} or blank.
     *
     * @param clientSupplied info sent in the request body (may be {@code null})
     * @param serverDetected info derived by {@link #detect(HttpServletRequest)}
     * @return merged {@link DeviceInfo}; never {@code null}
     */
    public DeviceInfo merge(DeviceInfo clientSupplied, DeviceInfo serverDetected) {
        if (clientSupplied == null) return serverDetected;

        return DeviceInfo.builder()
                .platform   (firstNonBlank(clientSupplied.getPlatform(),     serverDetected.getPlatform()))
                .userAgent  (firstNonBlank(clientSupplied.getUserAgent(),     serverDetected.getUserAgent()))
                .os         (firstNonBlank(clientSupplied.getOs(),            serverDetected.getOs()))
                .appVersion (firstNonBlank(clientSupplied.getAppVersion(),    serverDetected.getAppVersion()))
                .deviceModel(firstNonBlank(clientSupplied.getDeviceModel(),   serverDetected.getDeviceModel()))
                .locale     (firstNonBlank(clientSupplied.getLocale(),        serverDetected.getLocale()))
                // Kiosk fields are always client/server-set, never from UA parsing
                .kioskId        (clientSupplied.getKioskId())
                .kioskAuthMethod(clientSupplied.getKioskAuthMethod())
                .build();
    }

    // ── Platform detection ───────────────────────────────────────────────────

    /**
     * Derive a broad platform name from the {@code User-Agent} string.
     *
     * <p>Returns one of {@code "ios"}, {@code "android"}, {@code "web"},
     * or {@code "unknown"}.
     */
    private String detectPlatform(String ua) {
        if (ua == null) return "unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ipod")) {
            return "ios";
        }
        if (lower.contains("android")) {
            return "android";
        }
        // Electron / React-Native desktop shells
        if (lower.contains("electron")) {
            return "desktop";
        }
        // Typical browser UA strings contain "mozilla"
        if (lower.contains("mozilla") || lower.contains("chrome")
                || lower.contains("safari") || lower.contains("firefox")
                || lower.contains("edge")) {
            return "web";
        }
        return "unknown";
    }

    /**
     * Extract the OS name + version from the {@code User-Agent}.
     *
     * <p>Covers the most common patterns. Returns {@code null} if nothing
     * recognisable is found.
     */
    private String detectOs(String ua) {
        if (ua == null) return null;

        // iOS — e.g. "CPU iPhone OS 17_4 like Mac OS X"
        if (ua.contains("iPhone OS") || ua.contains("CPU OS")) {
            int start = ua.indexOf("OS ") + 3;
            int end   = ua.indexOf(' ', start);
            if (end < 0) end = ua.length();
            String ver = ua.substring(start, end).replace('_', '.');
            return "iOS " + ver;
        }

        // Android — e.g. "Android 14; ..."
        if (ua.contains("Android")) {
            int start = ua.indexOf("Android ") + 8;
            int end   = ua.indexOf(';', start);
            if (end < 0) end = ua.indexOf(')', start);
            if (end < 0) end = ua.length();
            return "Android " + ua.substring(start, Math.min(end, start + 8)).trim();
        }

        // Windows — e.g. "Windows NT 10.0"
        if (ua.contains("Windows NT")) {
            int start = ua.indexOf("Windows NT ") + 11;
            int end   = ua.indexOf(';', start);
            if (end < 0) end = ua.indexOf(')', start);
            if (end < 0) end = ua.length();
            String ntVer = ua.substring(start, Math.min(end, start + 6)).trim();
            return "Windows " + mapNtVersion(ntVer);
        }

        // macOS
        if (ua.contains("Mac OS X")) {
            int start = ua.indexOf("Mac OS X ") + 9;
            int end   = ua.indexOf(')', start);
            if (end < 0) end = ua.length();
            return "macOS " + ua.substring(start, Math.min(end, start + 12))
                    .replace('_', '.').trim();
        }

        // Linux
        if (ua.contains("Linux")) return "Linux";

        return null;
    }

    /** Maps NT version numbers to marketing Windows names. */
    private String mapNtVersion(String nt) {
        return switch (nt) {
            case "10.0" -> "10/11";
            case "6.3"  -> "8.1";
            case "6.2"  -> "8";
            case "6.1"  -> "7";
            default     -> "NT " + nt;
        };
    }

    // ── Locale ───────────────────────────────────────────────────────────────

    /** Returns the first language tag from {@code Accept-Language}, e.g. {@code "vi-VN"}. */
    private String primaryLocale(String acceptLang) {
        if (acceptLang == null || acceptLang.isBlank()) return null;
        // Accept-Language: vi-VN,vi;q=0.9,en-US;q=0.8
        String first = acceptLang.split("[,;]")[0].trim();
        return first.isEmpty() ? null : first;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String header(HttpServletRequest req, String name) {
        return Optional.ofNullable(req.getHeader(name))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    private String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
