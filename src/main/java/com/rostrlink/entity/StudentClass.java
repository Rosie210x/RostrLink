package com.rostrlink.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "student_classes")
@SQLDelete(sql = "UPDATE student_classes SET deleted_at = NOW() WHERE student_id = ? AND class_id = ?")
@Where(clause = "deleted_at IS NULL")
public class StudentClass {

    @EmbeddedId
    private StudentClassId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("studentId")
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("classId")
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    public void prePersist() {
        if (enrolledAt == null) enrolledAt = LocalDateTime.now();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentClassId implements Serializable {
        private Integer studentId;
        private Integer classId;
    }
}
