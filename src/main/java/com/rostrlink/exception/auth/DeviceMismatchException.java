package com.rostrlink.exception.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DeviceMismatchException extends RuntimeException {
    public DeviceMismatchException() {
        super("device_mismatch");
    }
}
