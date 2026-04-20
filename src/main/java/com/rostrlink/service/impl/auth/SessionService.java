package com.rostrlink.service.impl.auth;

import com.rostrlink.auth.config.AppProperties;
import com.rostrlink.redis.RedisSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    private static final String PERM_VERSION_PREFIX  = "user_permissions_version:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties props;

    public String create(RedisSession session) {
        String sessionId = UUID.randomUUID().toString();
        long ttl = "kiosk".equals(session.getSessionType())
                ? props.getSession().getKioskTtlSeconds()
                : props.getSession().getTtlSeconds();

        redisTemplate.opsForValue()
                .set(SESSION_PREFIX + sessionId, session, Duration.ofSeconds(ttl));

        redisTemplate.opsForSet()
                .add(USER_SESSIONS_PREFIX + session.getUserId(), sessionId);

        log.debug("Created session {} for user {}", sessionId, session.getUserId());
        return sessionId;
    }

    public Optional<RedisSession> get(String sessionId) {
        Object raw = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (raw instanceof RedisSession s) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    public void revoke(String sessionId, Long userId) {
        redisTemplate.delete(SESSION_PREFIX + sessionId);
        if (userId != null) {
            redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + userId, sessionId);
        }
        log.debug("Revoked session {}", sessionId);
    }

    public void revokeAllForUser(Long userId) {
        Set<Object> ids = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        if (ids != null) {
            ids.forEach(id -> redisTemplate.delete(SESSION_PREFIX + id));
        }
        redisTemplate.delete(USER_SESSIONS_PREFIX + userId);
        log.info("Revoked all sessions for user {}", userId);
    }

    public void touch(String sessionId, RedisSession session) {
        long ttl = "kiosk".equals(session.getSessionType())
                ? props.getSession().getKioskTtlSeconds()
                : props.getSession().getTtlSeconds();
        redisTemplate.expire(SESSION_PREFIX + sessionId, Duration.ofSeconds(ttl));
    }

    public int getCurrentPermissionsVersion(Long userId) {
        Object raw = redisTemplate.opsForValue().get(PERM_VERSION_PREFIX + userId);
        if (raw instanceof Number n) return n.intValue();
        return 0;
    }

    public void incrementPermissionsVersion(Long userId) {
        redisTemplate.opsForValue().increment(PERM_VERSION_PREFIX + userId);
    }

    public Set<Object> getSessionIdsForUser(Long userId) {
        return redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
    }
}