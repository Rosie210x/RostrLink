package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionJpaRepository extends JpaRepository<Session, UUID> {

    List<Session> findByUser_UserIdAndRevokedAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Session s SET s.revokedAt = :now, s.revokedBy = :by " +
            "WHERE s.user.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("now") OffsetDateTime now,
                         @Param("by") Long revokedBy);

    @Modifying
    @Query("UPDATE Session s SET s.lastActiveAt = :now WHERE s.sessionId = :id")
    void updateLastActive(@Param("id") UUID sessionId, @Param("now") OffsetDateTime now);
}

