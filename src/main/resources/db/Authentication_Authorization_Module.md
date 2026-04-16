# Authentication & Authorization Module

**Document type:** Combined SRS / SDD / SSD / Test Specification  
**System:** RostrLink (school pickup/attendance backend)  
**Prepared by:** Senior Software Architect & Security Engineer  
**Date:** 2026-04-15

> **Project excerpt:** RostrLink is a backend system managing student pickup/dropoff and attendance across multiple roles. The system supports roles: Admin, User, Parent, Teacher, Driver, Student, Kiosk.

---

## 1 Introduction

### Purpose
Provide a secure, auditable, and scalable **Authentication & Authorization** module for RostrLink. The module implements **server-side session-based authentication** (no JWT/stateless tokens), Role Based Access Control, context-based access control, OTP-based password reset, session lifecycle management, and anti-abuse controls.

### Scope

**In-scope**
- Login, logout, session creation, validation, revocation
- OTP-based forgot/reset password
- Server-side session store (Redis primary, PostgreSQL audit)
- RBAC and context-based permission checks
- Rate limiting, device fingerprinting, account lockout, CAPTCHA escalation hooks
- Middleware/filters for authentication, authorization, rate-limiting, and auditing
- Admin APIs for session listing and revocation

**Out-of-scope**
- OAuth2/OIDC federation as primary auth (can be integrated later)
- Stateless JWT issuance or client-managed tokens
- Client UI design

### Definitions
- **Authentication**: Verifying user identity.
- **Authorization**: Determining whether an authenticated user may perform an action.
- **Session**: Server-side record of an authenticated interaction identified by `session_id`.
- **RBAC**: Role Based Access Control.
- **Context-based access control**: Runtime constraints such as parent→child, teacher→class.
- **Device fingerprint**: Hash of client metadata used for risk decisions.
- **OTP**: One-Time Password for password reset or step-up authentication.

---

## 2 Functional Requirements

Each requirement is testable and traceable.

- **FR-01 Login**  
  Authenticate user with identifier + password. Support step-up OTP for new devices or high risk. On success create server-side session and set secure cookie.

- **FR-02 Logout**  
  Invalidate a session and remove server-side session record. Support user-initiated and admin-initiated revocation.

- **FR-03 Forgot password OTP**  
  Generate OTP, deliver via configured channel, verify OTP, allow password reset, and revoke existing sessions after reset.

- **FR-04 Session validation**  
  Validate session on every protected request by checking server-side session store.

- **FR-05 Session revocation and management**  
  List active sessions per user, revoke sessions, enforce concurrent session limits.

- **FR-06 Role Based Access Control**  
  Enforce permissions per role and module.

- **FR-07 Context-based access control**  
  Enforce runtime constraints: parent only accesses their children, teacher only assigned classes, driver only assigned events, kiosk limited scope.

- **FR-08 Rate limiting and anti-spam**  
  Apply per-IP and per-account rate limits for login and sensitive endpoints.

- **FR-09 Audit logging**  
  Record authentication events, session creation/revocation, and sensitive authorization decisions.

---

## 3 Non-Functional Requirements

### Security
- **NFR-Sec-01** Passwords hashed with Argon2id or bcrypt with strong parameters.
- **NFR-Sec-02** TLS 1.2+ enforced; HSTS enabled.
- **NFR-Sec-03** Cookies set with `HttpOnly`, `Secure`, `SameSite`.
- **NFR-Sec-04** CSRF protection for state-changing requests.
- **NFR-Sec-05** Rate limiting, account lockout, device fingerprinting, CAPTCHA escalation.

### Performance
- **NFR-Perf-01** Authentication middleware response target 50–150 ms under normal load.
- **NFR-Perf-02** Support 10,000 concurrent active sessions with Redis cluster and autoscaling.

### Scalability
- **NFR-Scale-01** Use Redis Cluster for session store; horizontal app server scaling.
- **NFR-Scale-02** Cache role_permissions and user_scope in Redis with invalidation.

### Availability
- **NFR-Avail-01** Authentication service SLA 99.9%.
- **NFR-Avail-02** Graceful degradation: if Redis unavailable, deny new sessions and allow safe read-only operations where appropriate.

---

## 4 System Architecture

### High-level architecture
```
Clients (Web, Mobile, Kiosk)
  └─ HTTPS (TLS)
     └─ API Gateway / Load Balancer
        └─ Filters: RateLimit -> Authentication -> Authorization -> Audit
           └─ Spring Boot App Servers (stateless)
              ├─ Authentication Service
              ├─ Authorization Service
              ├─ Business Services (events, logs, payments)
              └─ Background Workers (OTP delivery, audit persistence)
                 ├─ Redis Cluster (session store, rate-limit counters, caches)
                 └─ PostgreSQL (users, roles, audit, persistent sessions)
```

### Components
- **Authentication Service**: credential verification, session creation, OTP orchestration, account lockout.
- **Session Management**: Redis for active sessions; PostgreSQL for audit/persistent records.
- **Authorization Layer**: RBAC engine, context checker, permission cache.
- **Middleware / Filters**: AuthenticationFilter, AuthorizationFilter, RateLimitFilter, AuditFilter.
- **OTP Service**: generate/store/validate OTPs; integrate with SMS/Email providers.
- **Audit & Monitoring**: asynchronous persistence and SIEM integration.

### Data flow
1. Client `POST /auth/login` → API Gateway → RateLimitFilter.
2. AuthenticationService verifies credentials → create session in Redis + audit row in DB → set secure cookie.
3. Subsequent requests include cookie → AuthenticationFilter loads session from Redis → AuthorizationFilter evaluates RBAC and context → Business logic executes → Audit logs recorded.

---

## 5 Authentication Design

### Login flow
1. Client sends `POST /auth/login` with `{ identifier, password, device_info, client_nonce }` over TLS.
2. Rate-limit check by IP and identifier.
3. Verify password against stored hash. Record attempt in `login_attempts`.
4. Risk evaluation: if new device or geo anomaly, require OTP or CAPTCHA.
5. Create session: generate `session_id` (UUID), store session JSON in Redis with TTL, persist audit row in `sessions` table.
6. Return response with `Set-Cookie: SESSION_ID=<id>; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=<TTL>` and optional CSRF token.

### Logout flow
- `POST /auth/logout` reads `SESSION_ID` cookie, deletes `session:{session_id}` from Redis, updates `sessions.revoked_at` in DB, returns 200.

### OTP flow
1. `POST /auth/forgot-password` with `{ identifier }` generates OTP, stores hashed OTP in `otp_tokens` with TTL, sends via SMS/Email.
2. `POST /auth/verify-otp` validates OTP; on success allow password reset.
3. `POST /auth/reset-password` updates password hash, invalidates all sessions for user, logs audit.

### Session lifecycle
- **Creation**: on successful login; store in Redis with TTL and audit row in DB.
- **Validation**: middleware loads session from Redis, checks `expires_at`, `revoked_at`, device fingerprint.
- **Expiration**: TTL enforced by Redis; sliding expiration optional.
- **Revocation**: delete Redis key and set `revoked_at` in DB; admin/user can revoke.
- **Rotation**: rotate session id on privilege elevation and periodically.

### Cookie strategy
- `SESSION_ID` cookie: `HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=<TTL>`.
- CSRF protection: double-submit cookie or CSRF header token.
- Domain and path restrictions applied.

---

## 6 Authorization Design

### RBAC Model
- **Roles**: `Admin`, `User`, `Parent`, `Teacher`, `Driver`, `Student`, `Kiosk`.
- **Role assignment**: `user_roles` table with optional `scope` JSONB (e.g., `{"class_ids":[1,2]}`).
- **Hierarchy**: flat model; `Admin` can be granted all permissions via role_permissions.

### Permission Matrix

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
|  | update | ✓ | ✕ | ✕ | ✓ (assigned) | ✓ (assigned) | ✕ |
|  | delete | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
| **logs** | read | ✓ | ✓ (self) | ✓ (child logs) | ✓ (class logs) | ✓ (driver logs) | ✓ (kiosk logs) |
|  | create | ✓ | ✕ | ✓ (kiosk/parent) | ✓ (teacher) | ✓ (driver) | ✓ (kiosk) |
| **alerts** | read | ✓ | ✓ | ✓ (child alerts) | ✓ | ✓ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✕ | ✓ (arrived alert) | ✕ |
| **payments** | read | ✓ | ✓ (self) | ✕ | ✕ | ✕ | ✕ |
|  | create | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |
|  | update | ✓ | ✕ | ✕ | ✕ | ✕ | ✕ |

Legend: ✓ allowed; ✕ denied; `(scope)` indicates allowed only when resource is within user's scope.

### Context-Based Access Control
**Rules**
- Parent: access only to students where `student.id` ∈ `parent_children(user_id)`.
- Teacher: access only to classes in `teacher_assigned_classes(user_id)`.
- Driver: access only to events in `driver_assigned_events(user_id)`.
- Kiosk: limited to logs and actions within `kiosk_id` context.

**Enforcement**
1. Middleware checks role permission.
2. If permission is contextual, `ContextChecker` validates resource id against cached `user_scope` or DB.
3. Deny if resource not in scope.

**Caching**
- Cache `role_permissions` and `user_scope` in Redis with short TTL and invalidate on changes.

---

## 7 Database Design

### ER overview
Key entities: `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `sessions` (audit), `login_attempts`, `otp_tokens`.

### Table schemas

**users**
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE,
  password_hash TEXT NOT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
```

**roles**
```sql
CREATE TABLE roles (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL
);
```

**permissions**
```sql
CREATE TABLE permissions (
  id SERIAL PRIMARY KEY,
  module TEXT NOT NULL,
  action TEXT NOT NULL,
  description TEXT
);
CREATE UNIQUE INDEX permissions_module_action_idx ON permissions(module, action);
```

**role_permissions**
```sql
CREATE TABLE role_permissions (
  role_id INT REFERENCES roles(id) ON DELETE CASCADE,
  permission_id INT REFERENCES permissions(id) ON DELETE CASCADE,
  allowed BOOLEAN DEFAULT TRUE,
  constraint JSONB,
  PRIMARY KEY (role_id, permission_id)
);
```

**user_roles**
```sql
CREATE TABLE user_roles (
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
  role_id INT REFERENCES roles(id) ON DELETE CASCADE,
  scope JSONB,
  assigned_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, role_id)
);
```

**sessions**
```sql
CREATE TABLE sessions (
  id UUID PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  device_info TEXT,
  device_fingerprint TEXT,
  ip_address INET,
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  revoked_by BIGINT,
  metadata JSONB
);
```

**login_attempts**
```sql
CREATE TABLE login_attempts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NULL,
  identifier TEXT,
  ip_address INET,
  device_fingerprint TEXT,
  attempt_time TIMESTAMPTZ DEFAULT now(),
  success BOOLEAN
);
CREATE INDEX ON login_attempts (user_id, attempt_time DESC);
```

**otp_tokens**
```sql
CREATE TABLE otp_tokens (
  id UUID PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  otp_hash TEXT NOT NULL,
  channel TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  expires_at TIMESTAMPTZ,
  used BOOLEAN DEFAULT FALSE
);
```

Notes: Active sessions stored in Redis; `sessions` table is audit/persistent. Sensitive fields must be protected and never logged.

---

## 8 API Specification

All endpoints require TLS. Responses use envelope: `{ status, message, data, errorCode, errorData }`.

### POST /auth/login
**Request**
```json
{
  "identifier": "user@example.com",
  "password": "string",
  "device_info": "android:app-v1.2;device_id=abc",
  "client_nonce": "optional"
}
```
**Response 200**
```json
{
  "status": "ok",
  "message": "Logged in",
  "data": { "user": { "id": 123, "roles": ["Parent"] } }
}
```
Cookie `SESSION_ID` set.  
**Errors**
- 401 Unauthorized — invalid credentials
- 429 Too Many Requests — rate limit exceeded
- 403 Forbidden — account locked
- 202 Accepted — step-up required (OTP)

### POST /auth/logout
**Request:** Cookie `SESSION_ID` required.  
**Response 200**
```json
{ "status":"ok", "message":"Logged out" }
```
**Errors**
- 401 Unauthorized — no valid session

### POST /auth/forgot-password
**Request**
```json
{ "identifier": "user@example.com", "channel": "sms|email" }
```
**Response 200**
```json
{ "status":"ok", "message":"OTP sent if account exists" }
```
**Errors**
- 429 Too Many Requests — abuse detected

### POST /auth/verify-otp
**Request**
```json
{ "identifier":"user@example.com", "otp":"123456" }
```
**Response 200**
```json
{ "status":"ok", "message":"OTP verified", "data": { "reset_allowed": true } }
```
**Errors**
- 400 Bad Request — invalid/expired OTP

### POST /auth/reset-password
**Request**
```json
{ "identifier":"user@example.com", "otp":"123456", "new_password":"..." }
```
**Response 200**
```json
{ "status":"ok", "message":"Password reset; sessions revoked" }
```
**Errors**
- 400 Bad Request — invalid OTP or weak password

### GET /auth/me
**Request:** Cookie `SESSION_ID`  
**Response 200**
```json
{ "status":"ok", "data": { "id":123, "username":"x", "roles":["Parent"], "scope": {...} } }
```
**Errors**
- 401 Unauthorized

**Error envelope example**
```json
{ "status":"error", "message":"Too many attempts", "errorCode":"RATE_LIMIT", "errorData": { "retry_after": 60 } }
```

---

## 9 Security Design

### Threat model
- **Brute-force login**: automated credential guessing.
- **Session hijacking**: theft of session cookie.
- **Unauthorized access**: privilege escalation or bypassing context checks.
- **Replay attacks**: reuse of old session identifiers.
- **CSRF/XSS**: cross-site attacks to perform actions or steal tokens.

### Mitigations
- **Rate limiting**: Redis-based sliding window counters per IP and per identifier.
- **Account lockout**: lock after configurable failed attempts; unlock via admin or timed backoff.
- **CAPTCHA escalation**: present CAPTCHA after suspicious patterns.
- **Device/IP tracking**: store device fingerprint and IP; require OTP for new devices or geo anomalies.
- **Session security**:
    - `HttpOnly` + `Secure` + `SameSite` cookies.
    - Short TTL (e.g., 1 hour) with optional sliding refresh.
    - Session rotation on privilege elevation.
    - Revoke sessions on password reset or admin action.
- **CSRF protection**: double-submit cookie or CSRF header token.
- **XSS prevention**: input validation, output encoding, CSP headers.
- **Password hashing**: Argon2id recommended.
- **Audit & monitoring**: log authentication events and integrate with SIEM.
- **Transport security**: TLS 1.2+, HSTS, secure cipher suites.
- **Secrets management**: use vault for keys.

### Additional controls
- **Concurrent session limits**: configurable per user.
- **IP reputation & geo checks**: integrate threat intelligence.
- **Honeypots**: detect automated scanners.

---

## 10 Middleware and Filter Design

### Authentication middleware
**Responsibilities**
- Read `SESSION_ID` cookie.
- Load `session:{session_id}` from Redis.
- Validate `expires_at`, `revoked_at`, device fingerprint.
- Attach `SecurityContext` to request.
- Return 401 if invalid.

**Pseudocode**
```java
Filter authenticationFilter = (req, res, chain) -> {
  String sid = readCookie(req, "SESSION_ID");
  if (sid == null) { res.unauthorized(); return; }
  Session s = redis.get("session:" + sid);
  if (s == null || s.expiresAt.isBefore(now()) || s.revoked) {
    res.unauthorized(); return;
  }
  if (deviceMismatch(req, s)) {
    res.forbidden("device_mismatch"); return;
  }
  SecurityContext ctx = new SecurityContext(s.userId, s.roles, s.scope);
  req.setAttribute("security", ctx);
  chain.doFilter(req, res);
};
```

### Authorization middleware
**Responsibilities**
- Resolve module/action from route metadata.
- Fetch role_permissions from cache or DB.
- If contextual, call `ContextChecker` to validate resource id against user scope.
- Return 403 if not allowed.

**Pseudocode**
```java
Filter authorizationFilter = (req, res, chain) -> {
  SecurityContext ctx = req.getAttribute("security");
  ModuleAction ma = routeRegistry.getModuleAction(req.path, req.method);
  boolean allowed = rbacService.isAllowed(ctx.roles, ma);
  if (!allowed) { res.forbidden(); return; }
  if (ma.isContextual()) {
    boolean inScope = contextChecker.isInScope(ctx.userId, ma, req.params);
    if (!inScope) { res.forbidden(); return; }
  }
  chain.doFilter(req, res);
};
```

### RateLimitFilter
- Implement token-bucket or sliding window in Redis.
- Apply stricter limits for `/auth/login` and `/auth/forgot-password`.

### AuditFilter
- Capture sensitive actions and push to async queue for persistence.

---

## 11 Test Specification

### 11.1 Functional Test Cases

| Test Case ID | Description | Steps | Expected Result |
|---:|---|---|---|
| FT-01 | Login success | POST /auth/login with valid creds | 200, cookie set, session in Redis |
| FT-02 | Login failure | POST /auth/login with invalid password | 401, login_attempt recorded |
| FT-03 | Logout | POST /auth/logout with valid cookie | 200, session removed from Redis |
| FT-04 | Forgot password OTP send | POST /auth/forgot-password | 200, otp_tokens row created |
| FT-05 | Verify OTP success | POST /auth/verify-otp with valid OTP | 200, allow reset |
| FT-06 | Reset password | POST /auth/reset-password with valid OTP | 200, password updated, all sessions revoked |
| FT-07 | Session validation | Access protected endpoint with valid cookie | 200, SecurityContext present |
| FT-08 | RBAC deny | Parent attempts to delete user | 403 |

### 11.2 Security Test Cases

| Test Case ID | Description | Steps | Expected Result |
|---:|---|---|---|
| ST-01 | Brute-force protection | Simulate 100 failed logins from same IP | 429 after threshold, account lock if per-account threshold reached |
| ST-02 | Session hijacking attempt | Reuse stolen session cookie from different IP/device | If device mismatch configured → require OTP or deny; otherwise 401/403 |
| ST-03 | CSRF attempt | Submit state-changing request without CSRF token | 403 |
| ST-04 | Replay attack | Reuse old session id after revocation | 401 |

### 11.3 Edge Cases

| Test Case ID | Description | Steps | Expected Result |
|---:|---|---|---|
| EC-01 | Expired session | Use session after TTL | 401 |
| EC-02 | Concurrent sessions limit | Create sessions beyond limit | Oldest session revoked or new login denied per policy |
| EC-03 | Multiple devices | Login from new device triggers OTP | 202 require_otp or step-up flow |
| EC-04 | Redis outage | Simulate Redis unavailable | Deny new logins; allow safe read-only operations if configured |

---

## 12 Design Decisions and Trade-offs

### Why session-based instead of JWT
- **Revocation**: server-side sessions allow immediate revocation by deleting Redis key.
- **Context and device-awareness**: sessions can store device fingerprint and scope; JWT would still require server-side checks.
- **Security**: cookies with `HttpOnly` reduce token theft via XSS; JWT in localStorage is more exposed.
- **Operational control**: centralized session store enables admin tools to list and revoke sessions and enforce concurrent session limits.

### Pros and cons

| Aspect | Server-side sessions (Redis) | JWT (stateless) |
|---|---:|---:|
| Revocation | Immediate | Hard |
| Scalability | Good with Redis cluster | Very scalable per-server |
| Offline validation | Requires Redis | Self-contained |
| Device-awareness | Native | Requires server store |
| CSRF risk | Present but mitigable | Lower if Authorization header used |
| Complexity | Moderate | Simpler per-server but needs revocation strategy |

### Scalability considerations
- Use Redis Cluster with replication and persistence.
- Keep session payload minimal; store heavy metadata in PostgreSQL.
- Cache role_permissions and user_scope in Redis with invalidation hooks.
- Autoscale app servers; use DB read replicas for heavy read workloads.

---

## Appendix A Example Pseudocode

### Login
```java
public LoginResponse login(LoginRequest req) {
  String ip = req.getRemoteAddr();
  String deviceHash = DeviceFingerprint.hash(req.deviceInfo);

  if (rateLimiter.isBlocked(ip, req.identifier)) throw new TooManyRequests();
  if (accountService.isLocked(req.identifier)) throw new AccountLocked();

  boolean ok = authService.verifyPassword(req.identifier, req.password);
  loginAttemptService.record(req.identifier, ip, deviceHash, ok);

  if (!ok) {
    if (loginAttemptService.shouldLock(req.identifier)) accountService.lock(req.identifier);
    return Response.unauthorized();
  }

  if (deviceService.isNewDevice(req.userId, deviceHash)) {
    otpService.sendOtp(req.userId);
    return Response.accepted("require_otp");
  }

  UUID sessionId = UUID.randomUUID();
  Session s = new Session(sessionId, req.userId, roles, deviceHash, ip, now(), now().plusHours(1));
  redis.set("session:" + sessionId, s.toJson(), Duration.ofHours(1));
  sessionsRepository.insertAudit(s);
  cookieUtil.setSessionCookie(response, sessionId, 3600);
  return Response.ok(userDto);
}
```

### Authorization middleware
```java
public void doFilter(Request req, Response res, FilterChain chain) {
  String sid = cookieUtil.read(req, "SESSION_ID");
  if (sid == null) { res.unauthorized(); return; }
  Session s = redis.get("session:" + sid);
  if (s == null || s.isExpired() || s.isRevoked()) { res.unauthorized(); return; }
  SecurityContext ctx = new SecurityContext(s.userId, s.roles, s.scope);
  req.setAttribute("security", ctx);

  ModuleAction ma = routeRegistry.resolve(req);
  if (!rbacService.isAllowed(ctx.roles, ma)) { res.forbidden(); return; }
  if (ma.isContextual() && !contextChecker.isInScope(ctx.userId, ma, req)) { res.forbidden(); return; }
  chain.doFilter(req, res);
}
```

---

## Appendix B Operational Notes

- **Migration**: seed roles and permissions; migrate existing sessions to new model; update clients to use cookie-based auth.
- **Monitoring**: metrics for login success/failure, session creation rate, Redis latency, rate-limit triggers.
- **Admin UI**: session listing and revoke UI for support/security teams.
- **Testing**: red-team tests for session hijacking, CSRF, brute-force.
- **Compliance**: ensure audit retention meets regulatory requirements for student data.

