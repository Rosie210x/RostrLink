package com.rostrlink.service.impl.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rostrlink.entity.auth.User;
import com.rostrlink.entity.auth.UserAuditLog;
import com.rostrlink.repository.auth.UserAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final UserAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void recordAudit(Long userId, Long actorId, String action, Map<String, Object> changes, String ipAddress) {
        UserAuditLog logEntry = UserAuditLog.builder()
                .userId(userId)
                .actorId(actorId)
                .action(action)
                .changes(changes)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(logEntry);
    }

    /**
     * Compares two user objects and builds a diff map.
     * Format: { "fieldName": { "old": "...", "new": "..." } }
     */
    public Map<String, Object> buildDiff(User before, User after) {
        Map<String, Object> diff = new HashMap<>();

        compare(diff, "email", before.getEmail(), after.getEmail());
        compare(diff, "firstName", before.getFirstName(), after.getFirstName());
        compare(diff, "lastName", before.getLastName(), after.getLastName());
        compare(diff, "phoneNumber", before.getPhoneNumber(), after.getPhoneNumber());
        compare(diff, "status", before.getStatus(), after.getStatus());
        compare(diff, "avatarUrl", before.getAvatarUrl(), after.getAvatarUrl());

        return diff.isEmpty() ? null : diff;
    }

    private void compare(Map<String, Object> diff, String field, Object oldVal, Object newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            Map<String, Object> values = new HashMap<>();
            values.put("old", oldVal);
            values.put("new", newVal);
            diff.put(field, values);
        }
    }
}
