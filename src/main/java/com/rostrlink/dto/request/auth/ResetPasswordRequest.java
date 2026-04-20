package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /auth/reset-password */
@Data
public class ResetPasswordRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String otp;

    @NotBlank @Size(min = 8, max = 128)
    private String newPassword;
}
