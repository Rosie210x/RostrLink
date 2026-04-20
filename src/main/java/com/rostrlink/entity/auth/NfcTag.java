package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "nfc_tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NfcTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "nfc_id")
    private Long nfcId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @Column(name = "tag_code", unique = true, nullable = false)
    private String tagCode;

    @Column(name = "issued_at", updatable = false)
    @Builder.Default
    private OffsetDateTime issuedAt = OffsetDateTime.now();

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
