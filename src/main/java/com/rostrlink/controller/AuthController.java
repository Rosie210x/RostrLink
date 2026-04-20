package com.rostrlink.controller;

import com.rostrlink.dto.request.auth.ForgotPasswordRequest;
import com.rostrlink.dto.request.auth.LoginRequest;
import com.rostrlink.dto.request.auth.ResetPasswordRequest;
import com.rostrlink.dto.request.auth.VerifyOtpRequest;
import com.rostrlink.dto.response.ApiResponse;
import com.rostrlink.dto.response.LoginResponse;
import com.rostrlink.redis.RedisSession;
import com.rostrlink.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        return authService.login(req, request, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        return authService.logout(request, response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return authService.forgotPassword(req);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return authService.verifyOtp(req);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RedisSession>> me(HttpServletRequest request) {
        RedisSession session = (RedisSession) request.getAttribute("redisSession");
        return ResponseEntity.ok(ApiResponse.ok(session));
    }
}