package com.rostrlink.service.impl.auth;

import com.rostrlink.entity.auth.Role;
import com.rostrlink.repository.auth.RolePermissionRepository;
import com.rostrlink.repository.auth.RoleRepository;
import com.rostrlink.repository.auth.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startup validator that cross-checks the scope keys stored in
 * {@code user_roles.scope} against the permissions declared in
 * {@code role_permissions}.
 *
 * <h2>Problem being solved</h2>
 * <p>There are two separate access-control layers in the system:
 * <ol>
 *   <li><b>role_permissions</b> — coarse-grained feature access: which
 *       {@code module:action} a role may perform.</li>
 *   <li><b>user_roles.scope</b> — fine-grained row-level access: which
 *       specific resource IDs (e.g. {@code child_ids}, {@code class_ids})
 *       apply to a particular user-role assignment.</li>
 * </ol>
 *
 * <p>A scope key that has no backing permission is likely a data-entry error
 * (e.g. a parent has {@code class_ids} in scope but the Parent role has no
 * permission on the {@code class} module). This validator detects and logs
 * such mismatches at startup without blocking the application.
 *
 * <h2>What counts as a "match"</h2>
 * <p>A scope key {@code K} is considered backed if there exists at least one
 * {@code role_permission} row for the role where
 * {@code permission.module} equals the <em>singular</em> of {@code K}
 * (strips trailing {@code _ids} or {@code _id} suffix) or equals {@code K}
 * exactly.  This heuristic covers the most common naming conventions without
 * requiring a strict schema contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopePermissionValidator implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("ScopePermissionValidator: starting scope ↔ permission cross-check …");

        List<Role> roles = roleRepository.findAll();
        boolean clean = true;

        for (Role role : roles) {
            // Collect allowed modules for this role
            Set<String> allowedModules = rolePermissionRepository
                    .findAllowedByRoleIds(List.of(role.getRoleId()))
                    .stream()
                    .map(rp -> rp.getPermission().getModule())
                    .collect(Collectors.toSet());

            // Collect all distinct scope keys used by any user with this role
            Set<String> scopeKeys = collectScopeKeysForRole(role.getRoleId());

            for (String scopeKey : scopeKeys) {
                if (!isBacked(scopeKey, allowedModules)) {
                    log.warn(
                            "ScopePermissionValidator: scope key '{}' on role '{}' has no " +
                            "matching entry in role_permissions. This may indicate orphaned " +
                            "scope data or a missing permission row.",
                            scopeKey, role.getRoleName());
                    clean = false;
                }
            }
        }

        if (clean) {
            log.info("ScopePermissionValidator: all scope keys are backed by role_permissions — OK.");
        } else {
            log.warn("ScopePermissionValidator: one or more scope keys have no backing permission. " +
                     "Review the warnings above and either add the missing permissions or " +
                     "remove the orphaned scope keys from user_roles.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns all distinct JSONB scope keys used by active user-role rows
     * for the given role.
     */
    private Set<String> collectScopeKeysForRole(Integer roleId) {
        Set<String> keys = new HashSet<>();
        userRoleRepository.findActiveByRoleId(roleId, OffsetDateTime.now())
                .forEach(ur -> {
                    Map<String, Object> scope = ur.getScope();
                    if (scope != null) {
                        keys.addAll(scope.keySet());
                    }
                });
        return keys;
    }

    /**
     * Returns {@code true} if {@code scopeKey} can be matched to a module.
     *
     * <p>Matching rules (applied in order):
     * <ol>
     *   <li>Exact match: {@code scopeKey} equals an allowed module name.</li>
     *   <li>Strip {@code _ids} suffix: {@code "child_ids"} → {@code "child"}.</li>
     *   <li>Strip {@code _id} suffix: {@code "child_id"} → {@code "child"}.</li>
     * </ol>
     */
    private boolean isBacked(String scopeKey, Set<String> allowedModules) {
        if (allowedModules.contains(scopeKey)) return true;

        String stripped = scopeKey;
        if (stripped.endsWith("_ids")) {
            stripped = stripped.substring(0, stripped.length() - 4);
        } else if (stripped.endsWith("_id")) {
            stripped = stripped.substring(0, stripped.length() - 3);
        }
        return allowedModules.contains(stripped);
    }
}
