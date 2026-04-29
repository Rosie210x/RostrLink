package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.UserAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, Long> {

    Page<UserAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
