package com.rostrlink.dto.response;

import com.rostrlink.common.Role;
import com.rostrlink.common.UserStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Integer userId;

    private String username;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private UserStatus status;

    private Role role;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}