package com.rostrlink.entity.auth;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements java.io.Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "role_id")
    private Integer roleId;
}