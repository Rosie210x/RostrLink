package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

import com.rostrlink.common.ResetChannel;

@Entity
@Table(name = "otp_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpToken {

    @Id
    @Column(name = "otp_id", columnDefinition = "uuid")
    private java.util.UUID otpId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    /** "password_reset" | "step_up" | "new_device" */
    @Column(name = "purpose", nullable = false)
    @Builder.Default
    private String purpose = "password_reset";

    /**
     * Delivery channel — stored as lower-case string matching
     * {@link com.rostrlink.common.ResetChannel}.
     */
    @Column(name = "channel")
    private ResetChannel channel;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "used")
    @Builder.Default
    private Boolean used = false;
}