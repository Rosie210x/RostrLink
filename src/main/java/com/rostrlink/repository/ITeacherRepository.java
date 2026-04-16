package com.rostrlink.repository;

import com.rostrlink.entity.Teacher;
import com.rostrlink.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ITeacherRepository extends JpaRepository<Teacher, Integer> {
}
