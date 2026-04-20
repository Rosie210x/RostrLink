package com.rostrlink.exception.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException() {
        super("Session not found or expired");
    }
}
