package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    List<Permission> findByModule(String module);
    Optional<Permission> findByModuleAndAction(String module, String action);
}
