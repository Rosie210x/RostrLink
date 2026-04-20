package com.rostrlink.repository;

import com.rostrlink.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    List<User> searchUsers(int offset, int limit, String status, String role, String keyword);

    int countUsersWithFilter(String status, String role, String keyword);

    List<User> findAllByDeletedAtIsNull();

    boolean existsByEmailAndDeletedAtIsNullAndUserIdNot(String email, Long excludeUserId);

    boolean existsByPhoneNumberAndDeletedAtIsNullAndUserIdNot(String phoneNumber, Long excludeUserId);
}