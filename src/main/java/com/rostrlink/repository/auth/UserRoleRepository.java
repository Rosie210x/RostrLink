package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.UserRole;
import com.rostrlink.entity.auth.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.role " +
            "WHERE ur.user.userId = :userId " +
            "AND (ur.validUntil IS NULL OR ur.validUntil > :now)")
    List<UserRole> findActiveByUserId(@Param("userId") Long userId,
                                      @Param("now") OffsetDateTime now);
}