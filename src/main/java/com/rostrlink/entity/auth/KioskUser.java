package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "kiosk_users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "kiosk_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KioskUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kiosk_user_id")
    private Long kioskUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /** NULL = PIN valid at any kiosk; non-null = restricted to this kiosk only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kiosk_id")
    @ToString.Exclude
    private Kiosk kiosk;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}