package com.rostrlink.repository;

import com.rostrlink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Integer id);
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    List<User> searchUsers(int offset, int limit, String status, String role, String keyword);
    int countUsersWithFilter(String status, String role, String keyword);

    List<User> findAllByDeletedAtIsNull();

    boolean existsByEmailAndDeletedAtIsNullAndUserIdNot(String email, Integer excludeUserId);

    boolean existsByPhoneNumberAndDeletedAtIsNullAndUserIdNot(String phoneNumber, Integer excludeUserId);
}

