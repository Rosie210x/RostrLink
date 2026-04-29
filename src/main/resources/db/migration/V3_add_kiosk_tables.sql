-- V3__add_kiosk_tables.sql
-- Kiosk support for RostrLink Authentication & Authorization Module
-- Covers: FR-02, FR-13 (kiosk login, NFC/PIN flows, per-kiosk rate limiting, audit of scans)

-- ─────────────────────────────────────────────
-- kiosks
-- Registers physical kiosk devices (NFC readers / PIN terminals).
-- api_key_hash: kiosk authenticates requests to /auth/kiosk/* using a
-- pre-shared key (HMAC-SHA256). Store only the hash; rotate via admin API.
-- ─────────────────────────────────────────────
CREATE TABLE kiosks (
                        kiosk_id        BIGSERIAL PRIMARY KEY,
                        location        TEXT NOT NULL,                       -- e.g. "Gate A", "Classroom 3B"
                        description     TEXT,
                        api_key_hash    TEXT NOT NULL,                       -- hash of pre-shared kiosk API key
                        status          TEXT NOT NULL DEFAULT 'active',      -- active | maintenance | disabled
                        session_ttl_s   INT  NOT NULL DEFAULT 28800,         -- default kiosk session TTL (8 h)
                        failed_attempt_count INT DEFAULT 0,                  -- per-kiosk PIN brute-force counter
                        locked_until    TIMESTAMPTZ NULL,                    -- per-kiosk lockout (FR-13)
                        last_ping_at    TIMESTAMPTZ,
                        metadata        JSONB,
                        created_at      TIMESTAMPTZ DEFAULT now(),
                        updated_at      TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_kiosks_status ON kiosks(status);

-- ─────────────────────────────────────────────
-- kiosk_users
-- Optional PIN credentials for users who authenticate at kiosks via PIN
-- (as referenced in section 5.1.2 of the SDD: "stored in kiosk_users").
-- PIN is separate from the main users.password_hash so kiosk creds can
-- be rotated independently without touching the primary account.
-- ─────────────────────────────────────────────
CREATE TABLE kiosk_users (
                             kiosk_user_id   BIGSERIAL PRIMARY KEY,
                             user_id         BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                             kiosk_id        BIGINT NULL REFERENCES kiosks(kiosk_id) ON DELETE SET NULL,
                             pin_hash        TEXT NOT NULL,                       -- bcrypt hash of 6-digit PIN
                             active          BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at      TIMESTAMPTZ DEFAULT now(),
                             updated_at      TIMESTAMPTZ DEFAULT now(),
                             UNIQUE (user_id, kiosk_id)                           -- one PIN per user per kiosk scope
);
CREATE INDEX idx_kiosk_users_user    ON kiosk_users(user_id);
CREATE INDEX idx_kiosk_users_kiosk   ON kiosk_users(kiosk_id);

-- ─────────────────────────────────────────────
-- kiosk_sessions
-- Audit log for every NFC scan or PIN login at a kiosk.
-- Links to the main sessions table (session_type='kiosk') for lifecycle
-- management (revoke, list, permissions_version checks).
-- tap_out_at NULL means the user has not yet tapped out (open session).
-- ─────────────────────────────────────────────
CREATE TABLE kiosk_sessions (
                                kiosk_session_id BIGSERIAL PRIMARY KEY,
                                kiosk_id         BIGINT NOT NULL REFERENCES kiosks(kiosk_id),
                                user_id          BIGINT NOT NULL REFERENCES users(user_id),
                                nfc_id           BIGINT NULL REFERENCES nfc_tags(nfc_id),   -- set when auth method = NFC
                                kiosk_user_id    BIGINT NULL REFERENCES kiosk_users(kiosk_user_id), -- set when auth method = PIN
                                session_id       UUID   NULL REFERENCES sessions(session_id),       -- main session record
                                auth_method      TEXT NOT NULL DEFAULT 'nfc',        -- nfc | pin
                                ip_address       INET,
                                tapped_in_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                                tapped_out_at    TIMESTAMPTZ,
                                metadata         JSONB
);
CREATE INDEX idx_kiosk_sessions_kiosk   ON kiosk_sessions(kiosk_id);
CREATE INDEX idx_kiosk_sessions_user    ON kiosk_sessions(user_id);
CREATE INDEX idx_kiosk_sessions_nfc     ON kiosk_sessions(nfc_id);
CREATE INDEX idx_kiosk_sessions_tap_in  ON kiosk_sessions(tapped_in_at DESC);

-- ─────────────────────────────────────────────
-- Seed: roles for kiosk context (idempotent)
-- Matches RBAC design in section 6 of the SDD.
-- ─────────────────────────────────────────────
INSERT INTO roles (role_name, description)
VALUES ('kiosk', 'Limited-scope role for kiosk device sessions')
    ON CONFLICT (role_name) DO NOTHING;

-- ─────────────────────────────────────────────
-- Seed: kiosk-specific permissions (id empotent)
-- ─────────────────────────────────────────────
INSERT INTO permissions (module, action, description)
VALUES
    ('kiosk', 'nfc_scan',       'Scan NFC tag to create kiosk session'),
    ('kiosk', 'pin_login',      'Authenticate via PIN at kiosk'),
    ('kiosk', 'tap_out',        'Record tap-out event for a kiosk session'),
    ('kiosk', 'view_own_status','Kiosk user views own check-in status')
    ON CONFLICT (module, action) DO NOTHING;

-- Grant all kiosk permissions to the kiosk role
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM   roles r, permissions p
WHERE  r.role_name = 'kiosk'
  AND  p.module    = 'kiosk'
    ON CONFLICT (role_id, permission_id) DO NOTHING;