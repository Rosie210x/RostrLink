package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.RolePermission;
import com.rostrlink.entity.auth.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    /** Load all allowed permissions for a set of role IDs */
    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission " +
            "WHERE rp.role.roleId IN :roleIds AND rp.allowed = true")
    List<RolePermission> findAllowedByRoleIds(@Param("roleIds") List<Integer> roleIds);
}
