package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "devices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_fingerprint"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "device_fingerprint", nullable = false)
    private String deviceFingerprint;

    @Column(name = "device_info")
    private String deviceInfo;

    @Column(name = "first_seen_at", updatable = false)
    @Builder.Default
    private OffsetDateTime firstSeenAt = OffsetDateTime.now();

    @Column(name = "last_seen_at")
    @Builder.Default
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    @Column(name = "trusted")
    @Builder.Default
    private Boolean trusted = false;
}