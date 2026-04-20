package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/kiosk/nfc-scan */
@Data
public class NfcScanRequest {
    @NotBlank
    private String kioskId;

    @NotBlank
    private String tagCode;
}