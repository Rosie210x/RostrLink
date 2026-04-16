package com.rostrlink.service.impl;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.entity.User;
import com.rostrlink.exception.*;
import com.rostrlink.mapper.UserMapper;
import com.rostrlink.repository.IUserRepository;
import com.rostrlink.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UserService for catalog module (Users management).
 * - Uses soft-delete (deletedAt) and audit fields.
 * - Enforces uniqueness via repository checks and handles DB constraint exceptions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional

public class UserService implements IUserService {

    private final IUserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserValidator userValidator;

    /**
     * Create a new user.
     * - Validate basic uniqueness (username, email) before save for friendly errors.
     * - Encode password if provided (support external/OAuth users with null password).
     * - Set audit fields and default status.
     */
    @Override
    public UserResponse createUser(UserCreateRequest req) {
        userValidator.validateCreate(req);

        User user = userMapper.toUser(req);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        user.setCreatedDate(LocalDateTime.now());
        user.setUpdatedDate(LocalDateTime.now());

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    /**
     * Update an existing user.
     * - Only change the password when provided.
     * - Update audit fields.
     */
    @Override
    public UserResponse updateUser(UserUpdateRequest req) {
        User existing = userRepository.findByIdAndDeletedAtIsNull(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        userValidator.validateUpdate(req, existing);

        userMapper.updateUserFromRequest(req, existing);
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            existing.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        existing.setUpdatedDate(LocalDateTime.now());

        User saved = userRepository.save(existing);
        return userMapper.toResponse(saved);
    }

    /**
     * Soft-delete a user (set deletedAt and mark inactive).
     */
    @Override
    public void removeUser(Integer userId) {
        User existing = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userValidator.validateNotDeleted(existing);

        existing.setDeletedAt(LocalDateTime.now());
        existing.setUpdatedDate(LocalDateTime.now());
        userRepository.save(existing);
    }

    /**
     * Get user by id (non-deleted).
     */
    @Transactional(readOnly = true)
    @Override
    public UserResponse getUserById(Integer id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    /**
     * Search users with pagination and filters.
     * Repository should implement efficient search (projections to avoid N+1).
     */
    @Transactional(readOnly = true)
    @Override
    public List<UserResponse> searchUsers(int page, int size, String status, String role, String keyword) {
        int offset = Math.max(0, page) * Math.max(1, size);
        List<User> users = userRepository.searchUsers(offset, size, status, role, keyword);
        return users.stream().map(userMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Count pages for UI pagination.
     */
    @Transactional(readOnly = true)
    @Override
    public int getTotalPagesWithFilter(int size, String status, String role, String keyword) {
        int total = userRepository.countUsersWithFilter(status, role, keyword);
        if (size <= 0) return 0;
        return (int) Math.ceil((double) total / size);
    }

    /**
     * Return map userId -> displayName (username fallback).
     */
    @Transactional(readOnly = true)
    @Override
    public Map<Integer, String> getUserIdToUsernameMap() {
        return userRepository.findAllByDeletedAtIsNull()
                .stream()
                .collect(Collectors.toMap(
                        User::getUserId,
                        u -> Optional.of(u.getFirstName() + u.getLastName()).orElse(u.getUsername())
                ));
    }

    /**
     * Find user entity by username (used by auth).
     */
    @Transactional(readOnly = true)
    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user: " + username));
    }

    /**
     * Find user entity by id.
     */
    @Transactional(readOnly = true)
    @Override
    public User findById(Integer id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy user id = " + id));
    }

    // Additional convenience checks
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsernameAndDeletedAtIsNull(username);
    }

    @Override
    public boolean existsByEmail(String email, Integer excludeUserId) {
        return userRepository.existsByEmailAndDeletedAtIsNullAndUserIdNot(email, excludeUserId);
    }

    @Override
    public boolean existsByPhone(String phoneNumber, Integer excludeUserId) {
        return userRepository.existsByPhoneNumberAndDeletedAtIsNullAndUserIdNot(phoneNumber, excludeUserId);
    }

}
