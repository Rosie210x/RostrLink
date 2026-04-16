package com.rostrlink.controller;

import com.rostrlink.api.JsonResponse;
import com.rostrlink.common.Role;
import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.dto.response.UserResponse;
import com.rostrlink.service.impl.AvatarService;
import com.rostrlink.service.impl.EmailService;
import com.rostrlink.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for user management in RostrLink catalog module.
 * - Returns JsonResponse envelope used across the project.
 * - Supports avatar presigned upload flow and enqueueing transactional emails.
 * - Uses soft-delete and pagination/search filters.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final AvatarService avatarService;
    private final EmailService emailQueueService;

    // List users with pagination and filters
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<JsonResponse<Map<String, Object>>> listUsers(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        List<UserResponse> users = userService.searchUsers(page, size, status,
                role != null ? role.getDisplayName() : null, keyword);
        int totalPages = userService.getTotalPagesWithFilter(size, status,
                role != null ? role.getDisplayName() : null, keyword);

        Map<String, Object> payload = new HashMap<>();
        payload.put("users", users);
        payload.put("currentPage", page);
        payload.put("pageSize", size);
        payload.put("totalPages", totalPages);

        JsonResponse<Map<String, Object>> resp = JsonResponse.<Map<String, Object>>builder()
                .status("success")
                .message("Users fetched")
                .data(payload)
                .build();

        return ResponseEntity.ok(resp);
    }

    // Get user detail
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<JsonResponse<UserResponse>> getUser(@PathVariable("id") Integer id) {
        UserResponse user = userService.getUserById(id);
        JsonResponse<UserResponse> resp = JsonResponse.<UserResponse>builder()
                .status("success")
                .message("User detail")
                .data(user)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Create user
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request) {

        UserResponse created = userService.createUser(request);
        JsonResponse<UserResponse> resp = JsonResponse.<UserResponse>builder()
                .status("success")
                .message("User created")
                .data(created)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // Update user
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonResponse<UserResponse>> updateUser(
            @PathVariable("id") Integer id,
            @Valid @RequestBody UserUpdateRequest request) {

        request.setUserId(id);
        UserResponse updated = userService.updateUser(request);
        JsonResponse<UserResponse> resp = JsonResponse.<UserResponse>builder()
                .status("success")
                .message("User updated")
                .data(updated)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Soft-delete user
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonResponse<Object>> deleteUser(@PathVariable("id") Integer id) {
        userService.removeUser(id);
        JsonResponse<Object> resp = JsonResponse.builder()
                .status("success")
                .message("User deleted")
                .data(null)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Check username availability (convenience)
    @GetMapping("/check-username")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<JsonResponse<Map<String, Object>>> checkUsername(@RequestParam("username") String username) {
        boolean exists = userService.existsByUsername(username);
        Map<String, Object> meta = new HashMap<>();
        meta.put("available", !exists);
        JsonResponse<Map<String, Object>> resp = JsonResponse.<Map<String, Object>>builder()
                .status("success")
                .message("Username availability")
                .data(meta)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Generate presigned upload URL for avatar (S3 presigned PUT)
    @PostMapping("/{id}/avatar/upload-url")
    @PreAuthorize("hasRole('ADMIN') or (principal.username == @userService.findById(#id).getUsername())")
    public ResponseEntity<JsonResponse<Map<String, Object>>> requestAvatarUpload(
            @PathVariable("id") Integer id,
            @RequestParam("filename") String filename,
            @RequestParam(name = "contentType", defaultValue = "image/jpeg") String contentType) {

        // ensure user exists (throws NotFoundException if not)
        userService.findById(id);

        var presigned = avatarService.generateUpload(id.toString(), filename, contentType);

        Map<String, Object> data = new HashMap<>();
        data.put("uploadUrl", presigned.getUploadUrl());
        data.put("objectKey", presigned.getObjectKey());
        data.put("expiresInSeconds", presigned.getExpiresInSeconds());
        data.put("publicUrl", presigned.getPublicUrl());

        JsonResponse<Map<String, Object>> resp = JsonResponse.<Map<String, Object>>builder()
                .status("success")
                .message("Presigned upload URL generated")
                .data(data)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Confirm avatar uploaded and update user.avatarUrl
    @PostMapping("/{id}/avatar/confirm")
    @PreAuthorize("hasRole('ADMIN') or (principal.username == @userService.findById(#id).getUsername())")
    public ResponseEntity<JsonResponse<Object>> confirmAvatarUpload(
            @PathVariable("id") Integer id,
            @RequestBody Map<String, String> body) {

        String objectKey = body.get("objectKey");
        if (objectKey == null || objectKey.isBlank()) {
            JsonResponse<Object> err = JsonResponse.builder()
                    .status("error")
                    .message("objectKey is required")
                    .errorCode("INVALID_REQUEST")
                    .build();
            return ResponseEntity.badRequest().body(err);
        }

        // validate object exists and update user record
        avatarService.confirmUploaded(objectKey, id);
        JsonResponse<Object> resp = JsonResponse.builder()
                .status("success")
                .message("Avatar updated")
                .data(null)
                .build();
        return ResponseEntity.ok(resp);
    }

    // Enqueue transactional email (welcome, reset password, alerts)
    @PostMapping("/email/send")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<JsonResponse<Object>> sendEmail(@RequestBody Map<String, Object> payload) {
        // payload expected: { "to": "...", "template": "...", "data": {...} }
        String to = (String) payload.get("to");
        String template = (String) payload.get("template");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) payload.get("data");

        emailQueueService.enqueueEmail(to, template, data);

        JsonResponse<Object> resp = JsonResponse.builder()
                .status("success")
                .message("Email enqueued")
                .data(null)
                .build();
        return ResponseEntity.accepted().body(resp);
    }
}
