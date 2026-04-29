package com.rostrlink.dto.response.auth;

import lombok.Data;

import java.time.Instant;

/** Returned by GET /auth/devices */
@Data
public class DeviceInfo {
    private Long deviceId;
    private String deviceFingerprint;
    private String deviceInfoRaw;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private boolean trusted;
}
