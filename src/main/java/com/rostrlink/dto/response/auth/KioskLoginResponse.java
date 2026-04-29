package com.rostrlink.dto.response.auth;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/** Returned after a successful NFC scan or PIN login at a kiosk. */
@Data
@Builder
public class KioskLoginResponse {

    private Long kioskSessionId;
    private String sessionId;           // maps to sessions.session_id (UUID as String)
    private Long userId;
    private String fullName;
    private String authMethod;          // nfc | pin
    private OffsetDateTime tappedInAt;
    private Integer sessionTtlS;
}