package com.rostrlink.repository.auth;

import com.rostrlink.entity.auth.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ── Login (must work for non-deleted users) ──────────────────────────
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    // ── Single user (soft-delete-aware) ──────────────────────────────────
    @Query("SELECT u FROM User u WHERE u.userId = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") Long id);

    // ── Uniqueness checks (among non-deleted) ────────────────────────────
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmailActive(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL AND u.userId <> :excludeId")
    boolean existsByEmailActiveAndUserIdNot(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.phoneNumber = :phone AND u.deletedAt IS NULL AND u.userId <> :excludeId")
    boolean existsByPhoneActiveAndUserIdNot(@Param("phone") String phone, @Param("excludeId") Long excludeId);

    // ── Paginated search with filters ────────────────────────────────────
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> searchUsers(@Param("status") String status,
                           @Param("keyword") String keyword,
                           Pageable pageable);

}
