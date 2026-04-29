package com.rostrlink.dto.response;

import com.rostrlink.common.UserStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long userId;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private String avatarUrl;

    private UserStatus status;

    private List<String> roles;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}