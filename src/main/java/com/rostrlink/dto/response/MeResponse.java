package com.rostrlink.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Returned by GET /auth/me */
@Data
public class MeResponse {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private List<String> roles;
    private Map<String, Object> scope;
}
