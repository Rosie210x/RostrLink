package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/kiosk/tap-out */
@Data
public class KioskTapOutRequest {

    @NotBlank
    private String kioskId;

    /** The kiosk_session_id to close. */
    private Long kioskSessionId;
}