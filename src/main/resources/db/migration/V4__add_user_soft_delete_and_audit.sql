ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_by BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at);

CREATE TABLE IF NOT EXISTS user_audit_log (
    audit_id   BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    actor_id   BIGINT,
    action     TEXT         NOT NULL,   -- CREATE, UPDATE, DELETE, RESTORE
    changes    JSONB,                   -- {"field": {"old": ..., "new": ...}}
    ip_address TEXT,
    created_at TIMESTAMPTZ  DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_audit_user   ON user_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_audit_actor  ON user_audit_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_user_audit_action ON user_audit_log(action);
