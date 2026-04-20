package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "role_permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    @ToString.Exclude
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id")
    @ToString.Exclude
    private Permission permission;

    @Column(name = "allowed")
    @Builder.Default
    private Boolean allowed = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraint_json", columnDefinition = "jsonb")
    private Map<String, Object> constraintJson;
}