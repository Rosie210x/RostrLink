package com.rostrlink.service.impl;

import com.rostrlink.dto.request.UserCreateRequest;
import com.rostrlink.dto.request.UserUpdateRequest;
import com.rostrlink.entity.User;
import com.rostrlink.exception.ConflictException;
import com.rostrlink.exception.ValidationException;
import com.rostrlink.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class UserValidator {

    private final IUserRepository userRepository;

    public void validateCreate(UserCreateRequest req) {
        if (!StringUtils.hasText(req.getUsername())) {
            throw new ValidationException("username is required");
        }
        if (userRepository.existsByUsernameAndDeletedAtIsNull(req.getUsername())) {
            throw new ConflictException("username already exists");
        }
        if (StringUtils.hasText(req.getEmail()) &&
                userRepository.existsByEmailAndDeletedAtIsNull(req.getEmail())) {
            throw new ConflictException("email already exists");
        }
        if (req.getPassword() != null && req.getPassword().length() < 6) {
            throw new ValidationException("password must be at least 6 characters");
        }
    }

    public void validateUpdate(UserUpdateRequest req, User existing) {
        if (req.getEmail() != null && !req.getEmail().equals(existing.getEmail())) {
            if (userRepository.existsByEmailAndDeletedAtIsNull(req.getEmail())) {
                throw new ConflictException("email already exists");
            }
        }
        if (req.getUsername() != null && !req.getUsername().equals(existing.getUsername())) {
            if (userRepository.existsByUsernameAndDeletedAtIsNull(req.getUsername())) {
                throw new ConflictException("username already exists");
            }
        }
        if (req.getPassword() != null && req.getPassword().length() < 6) {
            throw new ValidationException("password must be at least 6 characters");
        }
    }

    public void validateNotDeleted(User user) {
        if (user == null) throw new ValidationException("entity is null");
        if (user.getDeletedAt() != null) throw new ValidationException("entity is deleted");
    }
}
