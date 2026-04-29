package com.rostrlink.dto.response.auth;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class LoginResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private Map<String, Object> scope;
    private String sessionType;
    private Instant expiresAt;
}