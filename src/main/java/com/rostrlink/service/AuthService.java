package com.rostrlink.service;

import com.rostrlink.dto.request.auth.*;
import com.rostrlink.dto.response.ApiResponse;
import com.rostrlink.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

public interface AuthService {
    @Transactional
    ResponseEntity<ApiResponse<LoginResponse>> login(LoginRequest req,
                                                     HttpServletRequest httpReq,
                                                     HttpServletResponse httpRes);

    @Transactional
    ResponseEntity<ApiResponse<LoginResponse>> kioskPinLogin(KioskPinLoginRequest req,
                                                             HttpServletRequest httpReq,
                                                             HttpServletResponse httpRes);

    @Transactional
    ResponseEntity<ApiResponse<LoginResponse>> nfcScan(NfcScanRequest req,
                                                       HttpServletRequest httpReq,
                                                       HttpServletResponse httpRes);

    @Transactional
    ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpReq,
                                             HttpServletResponse httpRes);

    // ─────────────────────────────────────────────────────────────────────────
    // Forgot password
    // ─────────────────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<Void>> forgotPassword(ForgotPasswordRequest req);

    // ─────────────────────────────────────────────────────────────────────────
    // Verify OTP
    // ─────────────────────────────────────────────────────────────────────────
    ResponseEntity<ApiResponse<Void>> verifyOtp(VerifyOtpRequest req);

    @Transactional
    ResponseEntity<ApiResponse<Void>> resetPassword(ResetPasswordRequest req);
}
