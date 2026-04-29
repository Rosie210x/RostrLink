package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** POST /auth/kiosk/pin-login */
@Data
public class KioskPinLoginRequest {

    @NotBlank
    private String kioskId;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "PIN must be exactly 6 digits")
    private String pin;
}