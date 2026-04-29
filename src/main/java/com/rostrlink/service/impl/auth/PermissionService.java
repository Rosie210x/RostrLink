package com.rostrlink.service.impl.auth;

import com.rostrlink.entity.auth.Role;
import com.rostrlink.entity.auth.UserRole;
import com.rostrlink.repository.auth.RolePermissionRepository;
import com.rostrlink.repository.auth.RoleRepository;
import com.rostrlink.repository.auth.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String ROLE_PERMISSIONS_PREFIX = "role_permissions:";
    private static final String USER_SCOPE_PREFIX = "user_scope:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public boolean isAllowed(List<String> roleNames, String module, String action) {
        return getPermissionsForRoles(roleNames).contains(module + ":" + action);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPermissionsForRoles(List<String> roleNames) {
        Set<String> result = new HashSet<>();
        for (String roleName : roleNames) {
            String cacheKey = ROLE_PERMISSIONS_PREFIX + roleName;
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            // Without global NON_FINAL typing the deserialised value is an ArrayList
            // (JSON array → ArrayList), not a Set — so we check Collection<?> instead.
            if (cached instanceof Collection<?> c) {
                result.addAll((Collection<String>) c);
                continue;
            }

            Set<String> fromDb = loadPermissionsFromDb(roleName);
            redisTemplate.opsForValue().set(cacheKey, fromDb, CACHE_TTL);
            result.addAll(fromDb);
        }
        return result;
    }

    private Set<String> loadPermissionsFromDb(String roleName) {
        Role role = roleRepository.findByRoleName(roleName).orElse(null);
        if (role == null || role.getRoleId() == null) {
            return Set.of();
        }

        return rolePermissionRepository.findAllowedByRoleIds(List.of(role.getRoleId()))
                .stream()
                .map(rp -> rp.getPermission().getModule() + ":" + rp.getPermission().getAction())
                .collect(java.util.stream.Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserScope(Long userId) {
        String cacheKey = USER_SCOPE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }

        Map<String, Object> scope = loadScopeFromDb(userId);
        redisTemplate.opsForValue().set(cacheKey, scope, CACHE_TTL);
        return scope;
    }

    private Map<String, Object> loadScopeFromDb(Long userId) {
        List<UserRole> userRoles = userRoleRepository.findActiveByUserId(userId, OffsetDateTime.now());
        Map<String, Object> merged = new HashMap<>();

        for (UserRole ur : userRoles) {
            if (ur.getScope() == null) {
                continue;
            }
            ur.getScope().forEach((k, v) -> merged.merge(k, v, (existing, incoming) -> {
                if (existing instanceof List<?> el && incoming instanceof List<?> il) {
                    List<Object> combined = new ArrayList<>((List<Object>) el);
                    combined.addAll((List<Object>) il);
                    return combined;
                }
                return incoming;
            }));
        }
        return merged;
    }

    public List<String> getRoleNamesForUser(Long userId) {
        return userRoleRepository.findActiveByUserId(userId, OffsetDateTime.now())
                .stream()
                .map(ur -> ur.getRole().getRoleName())
                .toList();
    }

    public void invalidateRoleCache(String roleName) {
        redisTemplate.delete(ROLE_PERMISSIONS_PREFIX + roleName);
        log.info("Invalidated permission cache for role {}", roleName);
    }

    public void invalidateUserScope(Long userId) {
        redisTemplate.delete(USER_SCOPE_PREFIX + userId);
        log.info("Invalidated scope cache for user {}", userId);
    }

    /**
     * Validates that every scope key in the active {@code user_roles} rows for
     * the given user is backed by at least one permission on the corresponding
     * module.
     *
     * <p>
     * A scope key {@code K} is considered backed if the user's role(s) have
     * an allowed {@code role_permission} whose {@code module} matches {@code K}
     * or {@code K} with a trailing {@code _ids}/{@code _id} suffix stripped.
     *
     * @param userId the user to validate
     * @return {@code true} if all scope keys are backed; {@code false} if any
     *         orphaned scope key is detected (details are logged as WARN)
     */
    public boolean validateUserScopeAgainstPermissions(Long userId) {
        List<String> roleNames = getRoleNamesForUser(userId);
        Set<String> allowedModules = new HashSet<>();
        for (String roleName : roleNames) {
            Role role = roleRepository.findByRoleName(roleName).orElse(null);
            if (role == null)
                continue;
            rolePermissionRepository.findAllowedByRoleIds(List.of(role.getRoleId()))
                    .forEach(rp -> allowedModules.add(rp.getPermission().getModule()));
        }

        Map<String, Object> scope = getUserScope(userId);
        boolean valid = true;
        for (String key : scope.keySet()) {
            if (!isScopeBacked(key, allowedModules)) {
                log.warn("User {}: scope key '{}' has no backing role_permission entry", userId, key);
                valid = false;
            }
        }
        return valid;
    }

    /** Mirrors the matching logic in {@link ScopePermissionValidator}. */
    private boolean isScopeBacked(String scopeKey, Set<String> allowedModules) {
        if (allowedModules.contains(scopeKey))
            return true;
        String stripped = scopeKey.endsWith("_ids")
                ? scopeKey.substring(0, scopeKey.length() - 4)
                : scopeKey.endsWith("_id")
                        ? scopeKey.substring(0, scopeKey.length() - 3)
                        : scopeKey;
        return allowedModules.contains(stripped);
    }
}
