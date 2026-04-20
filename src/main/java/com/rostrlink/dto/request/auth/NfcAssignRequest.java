package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** POST /auth/nfc/assign (Admin only) */
@Data
public class NfcAssignRequest {
    private Long userId;
    @NotBlank
    private String tagCode;
}
