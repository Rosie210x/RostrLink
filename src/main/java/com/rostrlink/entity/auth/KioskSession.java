package com.rostrlink.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kiosk_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KioskSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kiosk_session_id")
    private Long kioskSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kiosk_id", nullable = false)
    @ToString.Exclude
    private Kiosk kiosk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    /** Populated when auth_method = 'nfc'. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nfc_id")
    @ToString.Exclude
    private NfcTag nfcTag;

    /** Populated when auth_method = 'pin'. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kiosk_user_id")
    @ToString.Exclude
    private KioskUser kioskUser;

    /** Links to the main sessions table for lifecycle management. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @ToString.Exclude
    private Session session;

    @Column(name = "auth_method", nullable = false)
    @Builder.Default
    private String authMethod = "nfc";           // nfc | pin

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "tapped_in_at", nullable = false)
    @Builder.Default
    private OffsetDateTime tappedInAt = OffsetDateTime.now();

    @Column(name = "tapped_out_at")
    private OffsetDateTime tappedOutAt;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    public boolean isOpen() {
        return tappedOutAt == null;
    }
}