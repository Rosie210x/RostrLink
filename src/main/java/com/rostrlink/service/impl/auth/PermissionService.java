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
            if (cached instanceof Set<?> s) {
                result.addAll((Set<String>) s);
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
}