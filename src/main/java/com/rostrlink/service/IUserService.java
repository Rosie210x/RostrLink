package com.rostrlink.service;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.entity.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

public interface IUserService {
    UserResponse createUser(UserCreateRequest req);

    UserResponse updateUser(UserUpdateRequest req);

    void removeUser(Integer userId);

    @Transactional(readOnly = true)
    UserResponse getUserById(Integer id);

    @Transactional(readOnly = true)
    List<UserResponse> searchUsers(int page, int size, String status, String role, String keyword);

    @Transactional(readOnly = true)
    int getTotalPagesWithFilter(int size, String status, String role, String keyword);

    @Transactional(readOnly = true
    Map<Integer, String> getUserIdToUsernameMap();

    @Transactional(readOnly = true)
    User findByUsername(String username);

    @Transactional(readOnly = true)
    User findById(Integer id);

    // Additional convenience checks
    boolean existsByUsername(String username);

    boolean existsByEmail(String email, Integer excludeUserId);

    boolean existsByPhone(String phoneNumber, Integer excludeUserId);
}
