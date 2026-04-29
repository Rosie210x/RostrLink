package com.rostrlink.dto.response.auth;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Returned by GET /admin/kiosks or GET /admin/kiosks/{id}. */
@Data
@Builder
public class KioskResponse {

    private Long kioskId;
    private String location;
    private String description;
    private String status;
    private Integer sessionTtlS;
    private Integer failedAttemptCount;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastPingAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}