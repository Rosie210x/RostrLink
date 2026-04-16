-- Migration script (PostgreSQL) for RostrLink Authentication & Authorization
-- Date: 2026-04-16
-- Note: adjust tablespaces, owners, and extensions as needed.

-- Enable uuid-ossp if not present (for UUID generation)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create database (if running as superuser)
-- CREATE DATABASE rostrlink;
-- \c rostrlink

-- Roles & permissions core
CREATE TABLE roles (
                       id SERIAL PRIMARY KEY,
                       name TEXT NOT NULL UNIQUE,
                       description TEXT
);

CREATE TABLE permissions (
                             id SERIAL PRIMARY KEY,
                             module TEXT NOT NULL,
                             action TEXT NOT NULL,
                             description TEXT,
                             UNIQUE (module, action)
);

CREATE TABLE role_permissions (
                                  role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                  permission_id INT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                  allowed BOOLEAN DEFAULT TRUE,
                                  constraint_json JSONB,
                                  PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);

-- Users and profile
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       username TEXT NOT NULL UNIQUE,
                       password_hash TEXT NOT NULL,
                       email TEXT UNIQUE,
                       first_name TEXT,
                       last_name TEXT,
                       phone VARCHAR(30),
                       avatar_url TEXT,
                       status TEXT DEFAULT 'active', -- active | suspended | deleted
                       failed_attempt_count INT DEFAULT 0,
                       locked_until TIMESTAMPTZ NULL,
                       is_kiosk BOOLEAN DEFAULT FALSE,
                       created_at TIMESTAMPTZ DEFAULT now(),
                       updated_at TIMESTAMPTZ DEFAULT now(),
                       deleted_at TIMESTAMPTZ NULL
);
CREATE INDEX idx_users_email ON users(email);

-- user_roles (m:n) with scope JSONB
CREATE TABLE user_roles (
                            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                            scope JSONB,                -- e.g. {"class_ids":[...], "school_id":...}
                            valid_until TIMESTAMPTZ NULL,
                            updated_at TIMESTAMPTZ DEFAULT now(),
                            PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- Sessions (audit/persistent). Active session stored in Redis; this table for audit.
CREATE TABLE sessions (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          user_id UUID REFERENCES users(id),
                          session_type TEXT DEFAULT 'user', -- 'user' | 'kiosk' | 'api'
                          device_info TEXT,
                          device_fingerprint TEXT,
                          ip_address INET,
                          created_at TIMESTAMPTZ DEFAULT now(),
                          last_active_at TIMESTAMPTZ,
                          expires_at TIMESTAMPTZ,
                          revoked_at TIMESTAMPTZ,
                          revoked_by UUID,
                          permissions_version INT DEFAULT 0,
                          metadata JSONB
);
CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_expires ON sessions(expires_at);

-- Login attempts (audit)
CREATE TABLE login_attempts (
                                id BIGSERIAL PRIMARY KEY,
                                user_id UUID NULL REFERENCES users(id),
                                identifier TEXT, -- username/email/card_code
                                ip_address INET,
                                device_fingerprint TEXT,
                                attempt_time TIMESTAMPTZ DEFAULT now(),
                                success BOOLEAN
);
CREATE INDEX idx_login_attempts_user_time ON login_attempts(user_id, attempt_time DESC);

-- OTP tokens
CREATE TABLE otp_tokens (
                            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                            user_id UUID REFERENCES users(id),
                            otp_hash TEXT NOT NULL,
                            purpose TEXT NOT NULL DEFAULT 'password_reset', -- password_reset | step_up | new_device
                            channel TEXT, -- sms | email
                            created_at TIMESTAMPTZ DEFAULT now(),
                            expires_at TIMESTAMPTZ,
                            used BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_otp_user ON otp_tokens(user_id);

-- Kiosks (devices)
CREATE TABLE kiosks (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        name TEXT,
                        location TEXT,
                        kiosk_pin_hash TEXT, -- hashed PIN if kiosk supports PIN auth
                        created_at TIMESTAMPTZ DEFAULT now(),
                        deleted_at TIMESTAMPTZ NULL
);

-- NFC tags mapping (for students / parents)
CREATE TABLE nfc_tags (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          tag_code TEXT UNIQUE NOT NULL,
                          student_id UUID REFERENCES students(id) ON DELETE SET NULL,
                          issued_at TIMESTAMPTZ DEFAULT now(),
                          revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_nfc_tag_code ON nfc_tags(tag_code);

-- Wands (driver devices)
CREATE TABLE wands (
                       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                       wand_code TEXT UNIQUE NOT NULL,
                       driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
                       issued_at TIMESTAMPTZ DEFAULT now(),
                       revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_wand_code ON wands(wand_code);

-- Domain entities (converted from your MySQL schema to Postgres types)

-- Parents
CREATE TABLE parents (
                         id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                         user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         full_name TEXT,
                         phone VARCHAR(30),
                         created_at TIMESTAMPTZ DEFAULT now(),
                         deleted_at TIMESTAMPTZ NULL
);

-- Teachers
CREATE TABLE teachers (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          full_name TEXT,
                          phone VARCHAR(30),
                          created_at TIMESTAMPTZ DEFAULT now(),
                          deleted_at TIMESTAMPTZ NULL
);

-- Drivers
CREATE TABLE drivers (
                         id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                         user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                         full_name TEXT,
                         phone VARCHAR(30),
                         license_number TEXT,
                         created_at TIMESTAMPTZ DEFAULT now(),
                         deleted_at TIMESTAMPTZ NULL
);

-- Classes
CREATE TABLE classes (
                         id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                         name TEXT NOT NULL,
                         teacher_id UUID REFERENCES teachers(id) ON DELETE SET NULL,
                         created_at TIMESTAMPTZ DEFAULT now(),
                         deleted_at TIMESTAMPTZ NULL
);

-- Students
CREATE TABLE students (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          full_name TEXT NOT NULL,
                          date_of_birth DATE,
                          created_at TIMESTAMPTZ DEFAULT now(),
                          deleted_at TIMESTAMPTZ NULL
);

-- Student - Class relationship
CREATE TABLE student_classes (
                                 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                 student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                                 class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
                                 created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_student_classes_student ON student_classes(student_id);
CREATE INDEX idx_student_classes_class ON student_classes(class_id);

-- Student - Parent relationship
CREATE TABLE student_parents (
                                 id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                 student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                                 parent_id UUID NOT NULL REFERENCES parents(id) ON DELETE CASCADE
);
CREATE INDEX idx_student_parents_parent ON student_parents(parent_id);

-- Events & event_students
CREATE TABLE events (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        name TEXT,
                        type TEXT,
                        driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
                        status TEXT DEFAULT 'PENDING', -- PENDING | STARTED | ENDED
                        started_at TIMESTAMPTZ NULL,
                        ended_at TIMESTAMPTZ NULL,
                        created_at TIMESTAMPTZ DEFAULT now(),
                        deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE event_students (
                                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                                student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                                status TEXT DEFAULT 'PENDING' -- PENDING | ACCEPTED | DECLINED
);
CREATE INDEX idx_event_students_event ON event_students(event_id);
CREATE INDEX idx_event_students_student ON event_students(student_id);

-- Logs
CREATE TABLE teacher_logs (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
                              type TEXT NOT NULL, -- CHECK IN / CHECK OUT
                              logged_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE student_logs (
                              id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                              student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                              teacher_id UUID NOT NULL REFERENCES teachers(id) ON DELETE SET NULL,
                              type TEXT NOT NULL, -- CHECK_IN / CHECK_OUT
                              logged_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE parent_logs (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             parent_id UUID NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
                             student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                             kiosk_id UUID NOT NULL REFERENCES kiosks(id) ON DELETE SET NULL,
                             type TEXT NOT NULL, -- CHECK_IN / CHECK_OUT
                             logged_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE driver_logs (
                             id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                             driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
                             student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
                             event_id UUID REFERENCES events(id) ON DELETE SET NULL,
                             type TEXT NOT NULL, -- PICKUP / DROPOFF
                             logged_at TIMESTAMPTZ DEFAULT now()
);

-- Alerts
CREATE TABLE alerts (
                        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                        type TEXT,
                        message TEXT,
                        created_by UUID REFERENCES users(id) ON DELETE SET NULL,
                        created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE alert_targets (
                               id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                               alert_id UUID NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
                               user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               is_read BOOLEAN DEFAULT FALSE
);

CREATE TABLE alert_settings (
                                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                                user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                                enabled BOOLEAN DEFAULT TRUE,
                                updated_at TIMESTAMPTZ DEFAULT now()
);

-- Packages & billing
CREATE TABLE packages (
                          id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                          name TEXT,
                          price NUMERIC(10,2),
                          duration_days INT,
                          created_at TIMESTAMPTZ DEFAULT now(),
                          deleted_at TIMESTAMPTZ NULL
);

CREATE TABLE payment_bills (
                               id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
                               package_id UUID REFERENCES packages(id) ON DELETE SET NULL,
                               amount NUMERIC(10,2),
                               status TEXT DEFAULT 'PENDING', -- PENDING | PAID | CANCELLED
                               created_at TIMESTAMPTZ DEFAULT now()
);

-- Audit triggers / helper (optional): update updated_at on users
CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_timestamp();

-- Seed base roles and an admin user
INSERT INTO roles (name, description) VALUES
                                          ('ADMIN','System administrator'),
                                          ('USER','Regular user'),
                                          ('PARENT','Parent role'),
                                          ('TEACHER','Teacher role'),
                                          ('DRIVER','Driver role'),
                                          ('STUDENT','Student role'),
                                          ('KIOSK','Kiosk device role')
    ON CONFLICT DO NOTHING;

-- Seed example permissions (minimal set)
INSERT INTO permissions (module, action, description) VALUES
                                                          ('users','read','Read users'),
                                                          ('users','create','Create users'),
                                                          ('users','update','Update users'),
                                                          ('users','delete','Delete users'),
                                                          ('events','read','Read events'),
                                                          ('events','create','Create events'),
                                                          ('events','update','Update events'),
                                                          ('events','respond','Respond to event (accept/decline)'),
                                                          ('logs','create','Create logs'),
                                                          ('logs','read','Read logs')
    ON CONFLICT DO NOTHING;

-- Create an admin user (replace password_hash with real hash)
INSERT INTO users (id, username, password_hash, email, first_name, last_name)
VALUES (
           uuid_generate_v4(),
           'admin',
           -- Example bcrypt hash for "Admin@123" (replace in production)
           '$2b$12$EXAMPLEPLACEHOLDERHASHxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
           'admin@rostrlink.com',
           'System',
           'Admin'
       )
    ON CONFLICT (username) DO NOTHING;

-- Assign ADMIN role to admin user
WITH admin_user AS (
    SELECT id FROM users WHERE username = 'admin' LIMIT 1
    ), admin_role AS (
SELECT id FROM roles WHERE name = 'ADMIN' LIMIT 1
    )
INSERT INTO user_roles (user_id, role_id)
SELECT admin_user.id, admin_role.id FROM admin_user, admin_role
    ON CONFLICT DO NOTHING;

-- Indexes for performance (additional)
CREATE INDEX idx_events_driver ON events(driver_id);
CREATE INDEX idx_drivers_user ON drivers(user_id);
CREATE INDEX idx_teachers_user ON teachers(user_id);
CREATE INDEX idx_parents_user ON parents(user_id);

-- Retention policy notes (to be implemented via scheduled jobs):
-- - login_attempts: partition or purge older than configured retention (e.g., 3 years)
-- - sessions: archive older audit rows
-- - otp_tokens: delete expired tokens periodically

-- End of migration script
