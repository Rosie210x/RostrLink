package com.rostrlink.controller;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserPatchRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.dto.response.auth.ApiResponse;
import com.rostrlink.redis.RedisSession;
import com.rostrlink.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return userService.getAllUsers(status, keyword, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('Admin') or #id == authentication.principal")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest httpServletRequest) {
        RedisSession session = (RedisSession) httpServletRequest.getAttribute("redisSession");
        return userService.createUser(request, session.getUserId(), getIp(httpServletRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        RedisSession session = (RedisSession) httpServletRequest.getAttribute("redisSession");
        return userService.updateUser(id, request, session.getUserId(), getIp(httpServletRequest));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ApiResponse<UserResponse>> patch(
            @PathVariable Long id,
            @Valid @RequestBody UserPatchRequest request,
            HttpServletRequest httpServletRequest) {
        RedisSession session = (RedisSession) httpServletRequest.getAttribute("redisSession");
        return userService.patchUser(id, request, session.getUserId(), getIp(httpServletRequest));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            HttpServletRequest httpServletRequest) {
        RedisSession session = (RedisSession) httpServletRequest.getAttribute("redisSession");
        return userService.deleteUser(id, session.getUserId(), getIp(httpServletRequest));
    }

    private String getIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
