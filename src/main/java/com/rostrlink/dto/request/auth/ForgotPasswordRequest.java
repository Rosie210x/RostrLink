package com.rostrlink.dto.request.auth;

import com.rostrlink.common.ResetChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/forgot-password */
@Data
public class ForgotPasswordRequest {
    @NotBlank @Email
    private String email;

    /**
     * Delivery channel for the OTP code.
     * Accepted values: {@code "email"} (default), {@code "sms"}.
     * When omitted, the service layer defaults to {@link ResetChannel#EMAIL}.
     */
    private ResetChannel channel;
}

