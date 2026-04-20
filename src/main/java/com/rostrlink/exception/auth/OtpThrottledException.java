package com.rostrlink.exception.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class OtpThrottledException extends RuntimeException {
    public OtpThrottledException() {
        super("Too many OTP requests. Please wait before trying again.");
    }
}