package com.rostrlink.dto.response.auth;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Summary of a kiosk session — used in admin list views and audit queries. */
@Data
@Builder
public class KioskSessionResponse {

    private Long kioskSessionId;
    private Long kioskId;
    private String kioskLocation;
    private Long userId;
    private String userFullName;
    private String authMethod;
    private OffsetDateTime tappedInAt;
    private OffsetDateTime tappedOutAt;
    private boolean open;               // tappedOutAt == null
}

