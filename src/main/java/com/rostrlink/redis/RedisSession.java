package com.rostrlink.redis;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The session object stored in Redis under key {@code session:{sessionId}}.
 * Keep this payload minimal — it is loaded on every authenticated request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisSession implements Serializable {

    private Long userId;
    private String email;
    private List<String> roles;

    /** "user" | "kiosk" | "api" */
    private String sessionType;

    private String deviceFingerprint;
    private String ipAddress;

    /**
     * Monotonic version counter. If this is less than the current
     * {@code user_permissions_version:{userId}} value in Redis,
     * the session must be re-evaluated or revoked.
     */
    private int permissionsVersion;

    private Instant createdAt;
    private Instant expiresAt;

    /**
     * Context scope, e.g.:
     * <ul>
     *   <li>Parent: {@code {"child_ids": [1, 2]}}</li>
     *   <li>Teacher: {@code {"class_ids": [5, 6]}}</li>
     *   <li>Driver:  {@code {"event_ids": [10]}}</li>
     *   <li>Kiosk:   {@code {"kiosk_id": "K001"}}</li>
     * </ul>
     */
    private Map<String, Object> scope;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}