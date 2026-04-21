# Authentication & Authorization Module — Final (SRS / SDD / SSD / Test Specification)

**System:** RostrLink (school pickup/attendance backend)  
**Module:** Authentication & Authorization  
**Prepared by:** TamDHM  
**Date:** 2026-04-16

---

## Contents

1. Introduction 
2. Functional Requirements 
3. Non-Functional Requirements 
4. System Architecture 
5. Authentication Design 
6. Authorization Design 
7. Database Design 
8. API Specification 
9. Security Design 
10. Middleware / Filter Design
11. Test Specification 
12. Operational Runbooks & Monitoring 
13. Design Decisions & Trade-offs
14. Deliverables & Next Steps (migration scripts, Spring skeletons)

---

## 1. Introduction (SRS)

### 1.1 Purpose
Cung cấp một module xác thực và phân quyền an toàn, có thể kiểm toán và sẵn sàng triển khai cho RostrLink. Module dùng server-side sessions (Redis) hỗ trợ RBAC, kiểm soát truy cập theo ngữ cảnh, OTP cho reset mật khẩu, quản lý vòng đời session, cơ chế chống spam/brute-force, và audit đầy đủ.

### 1.2 Scope
**In-scope**
- Login, logout, session lifecycle (create/validate/expire/revoke/rotate)
- OTP flows (forgot password, step-up, new-device)
- Server-side session store (Redis) + audit/persistent `sessions` table (Postgres)
- RBAC + context-based access control (parent→child, teacher→class, driver→event, kiosk scope)
- Device management (trusted devices), kiosk (NFC/PIN) flows
- Rate limiting, account lockout, CAPTCHA escalation hooks
- Admin APIs: list/revoke sessions, manage devices, assign NFC tags
- Audit logging, monitoring, and runbooks for Redis/DB outages

**Out-of-scope**
- Primary OAuth2/OIDC federation (adapter notes included as future work)
- Client UI design (only API and backend behavior specified)

### 1.3 Definitions (glossary)
- **Authentication**: xác minh danh tính (password, OTP, card, PIN).
- **Authorization**: quyết định quyền thực hiện hành động (RBAC + context).
- **Session**: bản ghi phía server (Redis) định danh bằng `session_id`.
- **Kiosk**: thiết bị chấm công/check-in (NFC/PIN) với session và scope hạn chế.
- **Device fingerprint**: hash metadata client (UA, device-id, TLS hints) dùng cho risk decisions.
- **OTP**: one-time password; `otp_tokens.purpose` phân biệt luồng.
- **permissions_version**: số phiên bản quyền để force re-eval active sessions.

---

## 2. Functional Requirements (SRS)

Each requirement is testable and traceable.

- **FR-01 Login (password)** — Authenticate by identifier + password; risk-evaluate; create server-side session; set secure cookie.
- **FR-02 Login (kiosk)** — Authenticate by NFC tag or 6-digit PIN; create kiosk session with kiosk TTL and limited scope.
- **FR-03 Logout** — Invalidate session (user-initiated or admin-initiated); delete Redis key; update `sessions.revoked_at`.
- **FR-04 Forgot password (OTP)** — Generate OTP with `purpose=password_reset`; send via configured channel; verify and reset password; revoke all sessions.
- **FR-05 Step-up authentication** — For new device/geo anomaly or sensitive actions, require OTP or re-auth; rotate session id on elevation.
- **FR-06 Session validation** — Validate session on every protected request (Redis); check `expires_at`, `revoked_at`, `permissions_version`, device fingerprint.
- **FR-07 Session management** — List sessions, revoke single/all sessions, enforce concurrent session policy.
- **FR-08 RBAC** — Enforce userRole permissions and permission matrix; support multiple roles per user.
- **FR-09 Context-based access control** — Enforce parent/teacher/driver/kiosk constraints at runtime.
- **FR-10 Rate limiting & anti-spam** — Per-IP and per-account sliding window; CAPTCHA escalation and account lockout.
- **FR-11 Device management** — Register devices, mark trusted, list/revoke devices.
- **FR-12 Audit logging** — Persist authentication events, session lifecycle events, and sensitive authorization decisions.
- **FR-13 Kiosk flows** — NFC assignment, PIN login, per-kiosk rate limiting, audit of scans.
- **FR-14 Failover behavior** — Define behavior when Redis or DB unavailable (deny new sessions; allow safe read-only endpoints).

---

## 3. Non-Functional Requirements (SRS)

### Security
- Password hashing: Argon2id recommended; bcrypt acceptable with strong params.
- TLS 1.2+; HSTS; secure cipher suites.
- Cookies: `HttpOnly`, `Secure`, `SameSite=Strict` (or Lax where needed).
- CSRF protection: double-submit cookie or CSRF header token.
- Secrets stored in Vault/KMS.

### Performance
- Auth middleware latency target: 50–150 ms under normal load.
- Support 10,000 concurrent active sessions with Redis cluster and autoscaling.

### Scalability & Availability
- Redis Cluster with replication; Postgres primary + read replicas.
- SLA target: 99.9% for auth service.
- Graceful degradation: deny new sessions if Redis unavailable; allow read-only safe endpoints.

### Observability
- Metrics: login success/failure, session create/revoke, Redis latency, rate-limit triggers.
- Alerts for spikes in failed logins, Redis errors, permission invalidation failures.

---

## 4. System Architecture (SDD)

### 4.1 High-level diagram (text)
```
Clients (Web, Mobile, Kiosk)
  └─ HTTPS (TLS)
     └─ API Gateway / Load Balancer
        └─ Filters: RateLimit -> Authentication -> Authorization -> Audit
           └─ Spring Boot App Servers (stateless)
              ├─ Authentication Service
              ├─ Authorization Service
              ├─ Device & Kiosk Service
              ├─ OTP Service
              ├─ Background Workers (audit persistence, cache invalidation)
                 ├─ Redis Cluster (session store, counters, caches)
                 └─ PostgreSQL (users, roles, audit, persistent sessions)
```

### 4.2 Components
- **Authentication Service**: credential verification, OTP orchestration, device checks, session creation.
- **Session Management**: Redis primary store; Postgres `sessions` for audit.
- **Authorization Service**: RBAC engine, permission cache, ContextChecker.
- **Device Service**: manage `devices`, trusted flags, device fingerprinting.
- **Kiosk Service**: NFC/PIN flows, kiosk session TTL, per-kiosk rate limiting.
- **Background Workers**: process DB NOTIFY events, invalidate caches, persist audit asynchronously.

---

## 5. Authentication Design 

### 5.1 Login flows

#### 5.1.1 Standard login (web/mobile)
1. `POST /auth/login` with `{ identifier, password, device_info, client_nonce }`.
2. Rate-limit checks (IP + identifier).
3. Check `users.locked_until` and `status`.
4. Verify password (Argon2id). Record `login_attempts`.
5. Risk evaluation: device fingerprint unknown or geo anomaly → require OTP (step-up) or CAPTCHA.
6. On success: create session in Redis `session:{id}` (store `user_id`, `roles`, `scope`, `permissions_version`, `device_fingerprint`, `ip`, `session_type='user'`), insert audit row in `sessions` table.
7. Set cookie `SESSION_ID` with `HttpOnly; Secure; SameSite`. Return `200` and `user` info.

#### 5.1.2 Kiosk login (NFC or PIN)
- **NFC**: kiosk sends `tag_code` → server maps `nfc_tags.tag_code` → `user_id` → create kiosk session or record attendance event directly (configurable).
- **PIN**: kiosk sends `{ kiosk_id, pin }` → server verifies hashed PIN (stored in `kiosk_users` or `users` with userRole kiosk) → rate-limit per kiosk and per PIN identifier → create `session_type='kiosk'` with TTL longer/shorter per policy (recommended 8h or configurable).
- Kiosk sessions are device-bound (`device_fingerprint` = kiosk_id) and have **limited RBAC scope**.

### 5.2 Logout flow
- `POST /auth/logout` reads `SESSION_ID` cookie → delete `session:{id}` from Redis → update `sessions.revoked_at` in DB → return 200.

### 5.3 OTP flows
- `POST /auth/forgot-password` → create `otp_tokens` with `purpose='password_reset'`, TTL (10m), send via SMS/Email; throttle sends.
- `POST /auth/verify-otp` → validate OTP hash and `purpose`; mark used.
- `POST /auth/reset-password` → validate OTP, update `users.password_hash`, set `failed_attempt_count=0`, revoke all sessions for user.

### 5.4 Session lifecycle
- **Creation**: on login success; Redis key + audit row.
- **Validation**: middleware loads Redis session; checks `expires_at`, `revoked_at`, `permissions_version`, device fingerprint.
- **Expiration**: Redis TTL; sliding expiration optional (update `last_active_at` and refresh TTL).
- **Revocation**: delete Redis key; update `sessions.revoked_at`; remove from `user_sessions:{user_id}`.
- **Rotation**: rotate session id on privilege elevation (step-up) and optionally periodically.

### 5.5 Cookie & client storage
- **Web**: `SESSION_ID` cookie with `HttpOnly; Secure; SameSite`. CSRF token via double-submit cookie or header.
- **Mobile**: store session id in secure storage (Keychain/Keystore); send as cookie-like header over TLS. Avoid localStorage.
- **Kiosk**: store session id in kiosk secure storage; kiosk_id used as device_fingerprint.

---

## 6. Authorization Design (SDD)

### 6.1 RBAC model
- **Roles**: `Admin`, `User`, `Parent`, `Teacher`, `Driver`, `Student`, `Kiosk`.
- **user_roles**: many-to-many mapping with `scope` JSONB (e.g., `{"class_ids":[1,2]}`).
- **Permission resolution**: union of permissions from all roles assigned to user; contextual constraints applied after userRole check.

### 6.2 Permission matrix

| Module | Action | Admin | User | Parent | Teacher | Driver | Kiosk |
|---|---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **users** | read | ✓ | ✓ (self) | ✕ | ✕ | ✕ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
|  | update | ✓ | ✓ (self) | ✕ | ✕ | ✕ | ✕ |
|  | delete | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
| **students** | read | ✓ | ✓ (scoped) | ✓ (own children) | ✓ (assigned classes) | ✕ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✓ (class students) | ✕ | ✕ |
|  | update | ✓ | ✕ | ✕ | ✓ (class students) | ✕ | ✕ |
|  | delete | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
| **events** | read | ✓ | ✓ | ✓ (child events) | ✓ (assigned) | ✓ (assigned) | ✓ (kiosk scope) |
|  | create | ✓ | ✕ | ✕ | ✓ (teacher) | ✓ (driver) | ✕ |
|  | update | ✓ | ✕ | ✓ | ✓ (assigned) | ✓ (assigned) | ✕ |
|  | delete | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
| **logs** | read | ✓ | ✓ (self) | ✓ (child logs) | ✓ (class logs) | ✓ (driver logs) | ✓ (kiosk logs) |
|  | create | ✓ | ✕ | ✓ (kiosk/parent) | ✓ (teacher) | ✓ (driver) | ✓ (kiosk) |
| **alerts** | read | ✓ | ✓ | ✓ (child alerts) | ✓ | ✓ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✕ | ✓ (arrived alert) | ✕ |
| **payments** | read | ✓ | ✓ (self) | ✕ | ✕ | ✕ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
|  | update | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |

Legend: ✓ allowed; ✕ denied; `(scope)` indicates allowed only when resource is within user's scope.
### 6.3 Context-based access control
- **Rules**:
  - Parent → only their children (`parent_children(user_id)`).
  - Teacher → only assigned classes (`teacher_assigned_classes(user_id)`).
  - Driver → only assigned events (`driver_assigned_events(user_id)`).
  - Kiosk → only kiosk-scoped actions.
- **Enforcement**:
  1. Authorization middleware checks userRole permission (cached).
  2. If permission is contextual, `ContextChecker` validates resource id against `user_scope:{user_id}` (Redis cache) or DB fallback.
  3. Deny with 403 if not in scope.

### 6.4 Role change & immediate enforcement
- On `user_roles` or `role_permissions` change, publish event → worker invalidates caches and increments `permissions_version` for affected users → middleware compares and revokes or re-evaluates sessions.

---

## 7. Database Design (SDD) — Implementation-ready

> **See Section 7 in this document for full DDL.** (Included below for convenience.)

### 7.1 Core tables (DDL)

**users**
```sql
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
CREATE INDEX idx_users_email ON users(email);
```

**roles**
```sql
CREATE TABLE roles (
  role_id SERIAL PRIMARY KEY,
  role_name TEXT UNIQUE NOT NULL,
  description TEXT
);
```

**permissions**
```sql
CREATE TABLE permissions (
  permission_id SERIAL PRIMARY KEY,
  module TEXT NOT NULL,
  action TEXT NOT NULL,
  description TEXT,
  UNIQUE (module, action)
);
```

**role_permissions**
```sql
CREATE TABLE role_permissions (
  role_id INT REFERENCES roles(id) ON DELETE CASCADE,
  permission_id INT REFERENCES permissions(id) ON DELETE CASCADE,
  allowed BOOLEAN DEFAULT TRUE,
  constraint_json JSONB,
  PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
```

**user_roles**
```sql
CREATE TABLE user_roles (
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  role_id INT REFERENCES roles(id) ON DELETE CASCADE,
  scope JSONB,
  valid_until TIMESTAMPTZ NULL,
  updated_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
```

**sessions**
```sql
CREATE TABLE sessions (
  session_id UUID PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  session_type TEXT DEFAULT 'user', -- 'user' | 'kiosk' | 'api'
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
```

**login_attempts**
```sql
CREATE TABLE login_attempts (
  attempt_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NULL,
  identifier TEXT,
  ip_address INET,
  device_fingerprint TEXT,
  attempt_time TIMESTAMPTZ DEFAULT now(),
  success BOOLEAN
);
CREATE INDEX idx_login_attempts_user_time ON login_attempts(user_id, attempt_time DESC);
```

**otp_tokens**
```sql
CREATE TABLE otp_tokens (
  otp_id UUID PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  otp_hash TEXT NOT NULL,
  purpose TEXT NOT NULL DEFAULT 'password_reset',
  channel TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ,
  used BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_otp_user ON otp_tokens(user_id);
```

**devices**
```sql
CREATE TABLE devices (
  device_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  device_fingerprint TEXT NOT NULL,
  device_info TEXT,
  first_seen_at TIMESTAMPTZ DEFAULT now(),
  last_seen_at TIMESTAMPTZ DEFAULT now(),
  trusted BOOLEAN DEFAULT FALSE,
  UNIQUE (user_id, device_fingerprint)
);
CREATE INDEX idx_devices_user ON devices(user_id);
```

**nfc_tags**
```sql
CREATE TABLE nfc_tags (
  nfc_id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  tag_code TEXT UNIQUE NOT NULL,
  issued_at TIMESTAMPTZ DEFAULT now(),
  revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_nfc_user ON nfc_tags(user_id);
```

### 7.2 Redis key design
session:{session_id} — JSON session; TTL per session_type.

user_sessions:{user_id} — set of session ids.

role_permissions:{role_id} — cached permissions.

user_scope:{user_id} — cached scope JSON.

ratelimit:login:ip:{ip}, ratelimit:login:user:{identifier} — counters.

lockout:{identifier} — lockout expiry.

otp:{user_id}:{purpose} — OTP hash.
### 7.3 Cache invalidation & permission versioning
- Use Postgres `NOTIFY` or application event bus to publish userRole/scope changes.
- Worker invalidates `role_permissions:{role_id}`, `user_scope:{user_id}` and increments `permissions_version` for affected users (update `sessions.permissions_version` or maintain `user_permissions_version:{user_id}` in Redis).

### 7.4 Indexing, partitioning & retention
- Partition `login_attempts` by time if high volume.
- Retention: `login_attempts` 1–3 years; `sessions` audit 1–3 years (configurable). Archive to cold storage.

---

## 8. API Specification (OpenAPI-aligned)

> Responses use envelope: `{ status, message, data, errorCode, errorData }`. All endpoints require TLS.

### Authentication endpoints
- **POST /auth/login** — password login (supports step-up).
- **POST /auth/kiosk/pin-login** — kiosk PIN login (rate-limited).
- **POST /auth/kiosk/nfc-scan** — kiosk NFC scan (map tag → user).
- **POST /auth/logout** — invalidate current session.
- **POST /auth/forgot-password** — send OTP (purpose=password_reset).
- **POST /auth/verify-otp** — verify OTP.
- **POST /auth/reset-password** — reset password and revoke sessions.
- **GET /auth/me** — return current user, roles, scope.

### Session & device management
- **GET /auth/sessions** — list active sessions for current user.
- **POST /auth/sessions/revoke-all** — revoke all sessions for current user.
- **DELETE /auth/sessions/{session_id}** — revoke specific session.
- **GET /auth/devices** — list devices for current user.
- **POST /auth/devices/{device_id}/revoke** — revoke device and sessions bound to it.
- **POST /auth/nfc/assign** — admin assign `tag_code` → `user_id`.

### Error codes (examples)
- `401` Unauthorized — invalid session/credentials.
- `403` Forbidden — account locked or insufficient permission.
- `429` Too Many Requests — rate limit exceeded.
- `202` Accepted — step-up required (OTP).
- `503` Service Unavailable — maintenance or Redis outage.

*(Full OpenAPI YAML can be produced as a deliverable.)*

---

## 9. Security Design 

### 9.1 Threats & mitigations
- **Brute-force / credential stuffing**: Redis sliding window rate limiting; per-account counters; CAPTCHA escalation; account lockout (`locked_until`).
- **Session hijacking**: `HttpOnly` + `Secure` + `SameSite` cookies; device fingerprint binding; anomaly detection (IP/geo); session rotation.
- **Unauthorized access**: RBAC + context checks; permission versioning to enforce userRole changes.
- **Replay attacks**: immediate Redis delete on revoke; audit and alert.
- **CSRF/XSS**: CSRF tokens; CSP; input validation; HttpOnly cookies.

### 9.2 Password & OTP
- Passwords hashed with Argon2id (recommended).
- OTP hashed in DB; TTL per purpose; throttle OTP sends.

### 9.3 Secrets & keys
- Use Vault/KMS for DB credentials, SMS provider keys, and encryption keys. Rotate keys periodically.

### 9.4 Monitoring & detection
- SIEM integration for failed login spikes, session revocations, and suspicious device activity.
- Alerts for Redis latency, permission invalidation failures, and mass OTP sends.

---

## 10. Middleware / Filter Design (SDD)

### 10.1 Filter order (Spring Boot)
1. **RateLimitFilter** — global and per-route limits.
2. **AuthenticationFilter** — read `SESSION_ID`, validate Redis session, attach `SecurityContext`.
3. **AuthorizationFilter** — resolve module/action, check RBAC, call `ContextChecker` for contextual permissions.
4. **AuditFilter** — capture sensitive actions and push to async queue.

### 10.2 AuthenticationFilter (pseudocode)
```java
// simplified
String sid = cookieUtil.read(req, "SESSION_ID");
if (sid == null) { res.unauthorized(); return; }
Session s = redis.get("session:" + sid);
if (s == null || s.isExpired() || s.isRevoked()) { res.unauthorized(); return; }
if (deviceMismatch(req, s)) { res.forbidden("device_mismatch"); return; }
if (s.permissionsVersion < currentPermissionsVersion(s.userId)) { reevalOrRevoke(s); }
SecurityContext ctx = new SecurityContext(s.userId, s.roles, s.scope);
req.setAttribute("security", ctx);
chain.doFilter(req, res);
```

### 10.3 AuthorizationFilter (pseudocode)
```java
ModuleAction ma = routeRegistry.resolve(req);
if (!rbacService.isAllowed(ctx.roles, ma)) { res.forbidden(); return; }
if (ma.isContextual() && !contextChecker.isInScope(ctx.userId, ma, req.params)) { res.forbidden(); return; }
chain.doFilter(req, res);
```

---

## 11. Test Specification 

### 11.1 Functional test cases (sample)
| ID | Description | Steps | Expected |
|---:|---|---|---|
| FT-01 | Login success | POST /auth/login valid creds | 200, cookie set, session in Redis |
| FT-02 | Login failure | invalid password | 401, login_attempt recorded |
| FT-03 | Kiosk NFC scan | POST /auth/kiosk/nfc-scan with valid tag | 200, attendance logged |
| FT-04 | Kiosk PIN brute-force | 10 wrong PINs | 429/lockout per policy |
| FT-05 | Logout | POST /auth/logout | 200, session removed |

### 11.2 Security test cases
| ID | Description | Steps | Expected |
|---:|---|---|---|
| ST-01 | Brute-force protection | Simulate many failed logins | 429, account lockout after threshold |
| ST-02 | Session hijack | Use stolen cookie from different IP | deviceMismatch → 403 or step-up |
| ST-03 | CSRF | POST without CSRF token | 403 |
| ST-04 | Permission change | Change user_roles while session active | session re-eval or revoke |

### 11.3 Edge cases
- Redis outage simulation: deny new logins; allow read-only endpoints.
- Concurrent sessions beyond limit: oldest session revoked or new login denied per policy.
- OTP expiry/resend throttling.

---

## 12. Operational Runbooks & Monitoring 

### 12.1 Redis outage runbook (summary)
- Detect via metrics/alerts.
- Failover to replica/cluster; if failover fails → set maintenance mode: deny new logins, allow safe read-only endpoints.
- Notify support and security teams; rehydrate caches after recovery.

### 12.2 Emergency revoke
- Admin UI: bulk revoke by user or device.
- Operation: delete Redis keys, update `sessions.revoked_at`, publish audit event, notify user(s).

### 12.3 Monitoring & alerts
- Metrics: login success/failure rate, session creation rate, Redis latency, OTP send rate.
- Alerts: failed login spike, Redis errors, permission invalidation failures.

---

## 13. Design Decisions & Trade-offs

### 13.1 Why session-based (server-side) instead of JWT
- **Immediate revocation** (delete Redis key) — required for safety-critical operations.
- **Context & device-awareness** stored server-side; JWT would require server checks anyway.
- **Auditability**: `sessions` table + Redis keys provide full traceability.
- **Operational control**: list/revoke sessions, enforce concurrent limits.

### 13.2 Trade-offs
- **Stateful** requires Redis availability and HA; mitigated by cluster and runbooks.
- **CSRF** risk with cookies — mitigated by CSRF tokens and SameSite.
- **Scalability**: Redis cluster scales; keep session payload minimal.
---

## Appendix A — Short quotes from source document
> “Active sessions stored in Redis; `sessions` table is audit/persistent. Sensitive fields must be protected and never logged.”

---

