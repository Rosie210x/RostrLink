package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /auth/kiosk/pin-login */
@Data
public class KioskPinLoginRequest {
    @NotBlank
    private String kioskId;

    @NotBlank @Size(min = 6, max = 6)
    private String pin;
}
