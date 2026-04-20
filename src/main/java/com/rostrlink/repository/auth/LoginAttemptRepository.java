package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("SELECT COUNT(la) FROM LoginAttempt la " +
            "WHERE la.user.userId = :userId AND la.success = false " +
            "AND la.attemptTime > :since")
    long countFailedSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);
}