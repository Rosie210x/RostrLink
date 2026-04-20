package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/forgot-password */
@Data
public class ForgotPasswordRequest {
    @NotBlank @Email
    private String email;

    /** "email" | "sms" */
    private String channel = "email";
}
