package com.rostrlink.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /admin/kiosks  — register a new kiosk device */
@Data
public class KioskRegisterRequest {

    @NotBlank
    @Size(max = 255)
    private String location;

    @Size(max = 500)
    private String description;

    @NotBlank
    private String rawApiKey;           // server hashes this; never stored in plain text

    private Integer sessionTtlS;        // optional override; defaults to 28800 (8 h)
}