package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/verify-otp */
@Data
public class VerifyOtpRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String otp;

    /** Must match the purpose the OTP was issued for */
    @NotBlank
    private String purpose;
}
