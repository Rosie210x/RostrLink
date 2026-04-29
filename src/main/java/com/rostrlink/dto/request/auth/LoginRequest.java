package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /auth/login */
@Data
public class LoginRequest {
    @NotBlank @Email
    private String identifier;

    @NotBlank @Size(min = 8, max = 128)
    private String password;

    private DeviceInfo deviceInfo;
    /** Optional: client-side fingerprint hash (UA + device-id + hints) */
    private String clientFingerprint;
}