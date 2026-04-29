package com.rostrlink.service.impl.auth;

import com.rostrlink.common.UserStatus;
import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserPatchRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.dto.response.auth.ApiResponse;
import com.rostrlink.entity.auth.*;
import com.rostrlink.exception.NotFoundException;
import com.rostrlink.exception.ValidationException;
import com.rostrlink.exception.auth.EmailAlreadyExistsException;
import com.rostrlink.mapper.UserMapper;
import com.rostrlink.repository.auth.RoleRepository;
import com.rostrlink.repository.auth.UserRepository;
import com.rostrlink.repository.auth.UserRoleRepository;
import com.rostrlink.service.UserService;
import com.rostrlink.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final UserAuditService auditService;
    private final SessionService sessionService;

    @Override
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(String status, String keyword, Pageable pageable) {
        Page<User> users = userRepository.searchUsers(status, keyword, pageable);
        Page<UserResponse> responses = users.map(u -> {
            UserResponse resp = userMapper.toResponse(u);
            resp.setRoles(getUserRoleNames(u));
            return resp;
        });
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @Override
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(Long id) {
        User user = userRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        UserResponse resp = userMapper.toResponse(user);
        resp.setRoles(getUserRoleNames(user));
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<UserResponse>> createUser(UserCreateRequest request, Long actorId, String ipAddress) {
        if (userRepository.existsByEmailActive(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = userMapper.toUser(request);
        user.setPasswordHash(passwordUtil.hash(request.getPassword()));
        user.setStatus(request.getStatus());
        
        final User savedUser = userRepository.save(user);

        // Assign Role
        assignRole(savedUser, request.getUserRole().name());

        // Audit
        auditService.recordAudit(savedUser.getUserId(), actorId, "CREATE", 
                auditService.buildDiff(new User(), savedUser), ipAddress);

        UserResponse resp = userMapper.toResponse(savedUser);
        resp.setRoles(List.of(request.getUserRole().name()));
        return ResponseEntity.ok(ApiResponse.ok("User created successfully", resp));
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(Long id, UserUpdateRequest request, Long actorId, String ipAddress) {

        User user = userRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        // Email is REQUIRED in PUT
        if (userRepository.existsByEmailActiveAndUserIdNot(request.getEmail(), id)) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User before = cloneUser(user);

        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAvatarUrl(request.getAvatarUrl());

        user.setStatus(request.getStatus());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordUtil.hash(request.getPassword()));
        } else {
            user.setPasswordHash(null);
        }

        userRepository.save(user);

        if (request.getUserRole() != null) {
            replaceRoles(user, request.getUserRole().name());
        } else {
            clearRoles(user);
        }

        // Audit
        Map<String, Object> diff = auditService.buildDiff(before, user);
        if (diff != null) {
            auditService.recordAudit(user.getUserId(), actorId, "UPDATE", diff, ipAddress);
        }

        UserResponse resp = userMapper.toResponse(user);
        resp.setRoles(getUserRoleNames(user));

        return ResponseEntity.ok(ApiResponse.ok("User updated successfully", resp));
    }

    private void clearRoles(User user) {
        userRoleRepository.deleteByUserId(user.getUserId());
    }

    @Transactional
    @Override
    public ResponseEntity<ApiResponse<UserResponse>> patchUser(Long id, UserPatchRequest request, Long actorId, String ipAddress) {

        User user = userRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        if (request.getEmail() != null &&
                userRepository.existsByEmailActiveAndUserIdNot(request.getEmail(), id)) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User before = cloneUser(user);

        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordUtil.hash(request.getPassword()));
        }

        userRepository.save(user);

        // PATCH → chỉ update role nếu có gửi
        if (request.getUserRole() != null) {
            replaceRoles(user, request.getUserRole().name());
        }

        // Audit
        Map<String, Object> diff = auditService.buildDiff(before, user);
        if (diff != null) {
            auditService.recordAudit(user.getUserId(), actorId, "PATCH", diff, ipAddress);
        }

        UserResponse resp = userMapper.toResponse(user);
        resp.setRoles(getUserRoleNames(user));

        return ResponseEntity.ok(ApiResponse.ok("User patched successfully", resp));
    }
    @Transactional
    @Override
    public ResponseEntity<ApiResponse<Void>> deleteUser(Long id, Long actorId, String ipAddress) {
        User user = userRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        user.setDeletedAt(OffsetDateTime.now());
        user.setDeletedBy(actorId);
        userRepository.save(user);

        // Revoke sessions
        sessionService.revokeAllForUser(id);

        // Audit
        auditService.recordAudit(id, actorId, "DELETE", Collections.singletonMap("deleted", true), ipAddress);

        return ResponseEntity.ok(ApiResponse.ok("User deleted successfully", null));
    }

    private void assignRole(User user, String roleName) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new ValidationException("Role not found: " + roleName));
        
        UserRole ur = UserRole.builder()
                .id(new UserRoleId(user.getUserId(), role.getRoleId()))
                .user(user)
                .role(role)
                .build();
        userRoleRepository.save(ur);
        
        // Invalidate cache
        sessionService.incrementPermissionsVersion(user.getUserId());
    }

    private void replaceRoles(User user, String roleName) {
        // Find existing active roles for this user
        List<UserRole> existing = userRoleRepository.findActiveByUserId(user.getUserId(), OffsetDateTime.now());
        
        // Simple logic for this requirement: replace all with the one provided
        for (UserRole ur : existing) {
            ur.setValidUntil(OffsetDateTime.now());
            userRoleRepository.save(ur);
        }
        
        assignRole(user, roleName);
    }

    private List<String> getUserRoleNames(User user) {
        return userRoleRepository.findActiveByUserId(user.getUserId(), OffsetDateTime.now())
                .stream()
                .map(ur -> ur.getRole().getRoleName())
                .toList();
    }

    private User cloneUser(User u) {
        return User.builder()
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .phoneNumber(u.getPhoneNumber())
                .status(u.getStatus())
                .avatarUrl(u.getAvatarUrl())
                .build();
    }
}
