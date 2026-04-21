-- V1__create_auth_tables.sql
-- Authentication & Authorization core schema for RostrLink

-- users
CREATE TABLE users (
                       user_id BIGSERIAL PRIMARY KEY,
                       email TEXT UNIQUE,
                       first_name TEXT NOT NULL,
                       last_name TEXT NOT NULL,
                       password_hash TEXT NOT NULL,
                       phone_number TEXT,
                       avatar_url TEXT,
                       status TEXT DEFAULT 'active',
                       failed_attempt_count INT DEFAULT 0,
                       locked_until TIMESTAMPTZ NULL,
                       created_at TIMESTAMPTZ DEFAULT now(),
                       updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- roles
CREATE TABLE roles (
                       role_id SERIAL PRIMARY KEY,
                       role_name TEXT UNIQUE NOT NULL,
                       description TEXT
);

-- permissions
CREATE TABLE permissions (
                             permission_id SERIAL PRIMARY KEY,
                             module TEXT NOT NULL,
                             action TEXT NOT NULL,
                             description TEXT,
                             UNIQUE (module, action)
);

-- role_permissions (sửa FK tham chiếu đúng)
CREATE TABLE role_permissions (
                                  role_id INT REFERENCES roles(role_id) ON DELETE CASCADE,
                                  permission_id INT REFERENCES permissions(permission_id) ON DELETE CASCADE,
                                  allowed BOOLEAN DEFAULT TRUE,
                                  constraint_json JSONB,
                                  PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);

-- user_roles (sửa FK tham chiếu đúng)
CREATE TABLE user_roles (
                            user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
                            role_id INT REFERENCES roles(role_id) ON DELETE CASCADE,
                            scope JSONB,
                            valid_until TIMESTAMPTZ NULL,
                            updated_at TIMESTAMPTZ DEFAULT now(),
                            PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- sessions (sửa FK tham chiếu đúng)
CREATE TABLE sessions (
                          session_id UUID PRIMARY KEY,
                          user_id BIGINT REFERENCES users(user_id),
                          session_type TEXT DEFAULT 'user',
                          device_info TEXT,
                          device_fingerprint TEXT,
                          ip_address INET,
                          created_at TIMESTAMPTZ DEFAULT now(),
                          last_active_at TIMESTAMPTZ,
                          expires_at TIMESTAMPTZ,
                          revoked_at TIMESTAMPTZ,
                          revoked_by BIGINT,
                          permissions_version INT DEFAULT 0,
                          metadata JSONB
);
CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_expires ON sessions(expires_at);

-- login_attempts (sửa FK tham chiếu đúng)
CREATE TABLE login_attempts (
                                attempt_id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NULL REFERENCES users(user_id),
                                identifier TEXT,
                                ip_address INET,
                                device_fingerprint TEXT,
                                attempt_time TIMESTAMPTZ DEFAULT now(),
                                success BOOLEAN
);
CREATE INDEX idx_login_attempts_user_time ON login_attempts(user_id, attempt_time DESC);

-- otp_tokens (sửa FK tham chiếu đúng)
CREATE TABLE otp_tokens (
                            otp_id UUID PRIMARY KEY,
                            user_id BIGINT REFERENCES users(user_id),
                            otp_hash TEXT NOT NULL,
                            purpose TEXT NOT NULL DEFAULT 'password_reset',
                            channel TEXT,
                            created_at TIMESTAMPTZ DEFAULT now(),
                            expires_at TIMESTAMPTZ,
                            used BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_otp_user ON otp_tokens(user_id);

-- devices (sửa FK tham chiếu đúng, giữ UNIQUE)
CREATE TABLE devices (
                         device_id BIGSERIAL PRIMARY KEY,
                         user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
                         device_fingerprint TEXT NOT NULL,
                         device_info TEXT,
                         first_seen_at TIMESTAMPTZ DEFAULT now(),
                         last_seen_at TIMESTAMPTZ DEFAULT now(),
                         trusted BOOLEAN DEFAULT FALSE,
                         UNIQUE (user_id, device_fingerprint)
);
CREATE INDEX idx_devices_user ON devices(user_id);

-- nfc_tags (sửa FK tham chiếu đúng)
CREATE TABLE nfc_tags (
                          nfc_id BIGSERIAL PRIMARY KEY,
                          user_id BIGINT REFERENCES users(user_id) ON DELETE CASCADE,
                          tag_code TEXT UNIQUE NOT NULL,
                          issued_at TIMESTAMPTZ DEFAULT now(),
                          revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_nfc_user ON nfc_tags(user_id);
