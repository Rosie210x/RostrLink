package com.rostrlink.service;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserPatchRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.dto.response.auth.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface UserService {
    ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(String status, String keyword, Pageable pageable);
    ResponseEntity<ApiResponse<UserResponse>> getUserById(Long id);
    ResponseEntity<ApiResponse<UserResponse>> createUser(UserCreateRequest request, Long actorId, String ipAddress);
    ResponseEntity<ApiResponse<UserResponse>> updateUser(Long id, UserUpdateRequest request, Long actorId, String ipAddress);
    ResponseEntity<ApiResponse<UserResponse>> patchUser(Long id, UserPatchRequest request, Long actorId, String ipAddress);
    ResponseEntity<ApiResponse<Void>> deleteUser(Long id, Long actorId, String ipAddress);
}
