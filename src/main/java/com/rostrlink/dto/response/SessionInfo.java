package com.rostrlink.dto.response;

import lombok.Data;
import java.time.Instant;


/** Returned by GET /auth/sessions */
@Data
public class SessionInfo {
    private String sessionId;
    private String sessionType;
    private String deviceInfo;
    private String ipAddress;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private boolean current;
}
