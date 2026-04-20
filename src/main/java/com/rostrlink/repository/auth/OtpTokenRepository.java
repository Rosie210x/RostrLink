package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    /** Find the latest unused, unexpired OTP for a user + purpose */
    @Query("SELECT o FROM OtpToken o WHERE o.user.userId = :userId " +
            "AND o.purpose = :purpose AND o.used = false AND o.expiresAt > :now " +
            "ORDER BY o.createdAt DESC")
    List<OtpToken> findValidOtps(@Param("userId") Long userId,
                                 @Param("purpose") String purpose,
                                 @Param("now") OffsetDateTime now);

    @Query("SELECT COUNT(o) FROM OtpToken o WHERE o.user.userId = :userId " +
            "AND o.purpose = :purpose AND o.createdAt > :since")
    long countSentSince(@Param("userId") Long userId,
                        @Param("purpose") String purpose,
                        @Param("since") OffsetDateTime since);
}