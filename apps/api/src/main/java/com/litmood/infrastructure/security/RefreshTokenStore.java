package com.litmood.infrastructure.security;

import java.time.Duration;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰 화이트리스트 (ADR-005, ADR-009).
 *
 * JWT 는 본래 무상태지만, refresh 만큼은 서버가 상태를 갖는다.
 * 그래야 (1) 즉시 강제 로그아웃과 (2) 탈취 토큰 재사용 감지가 가능하다.
 * Access 토큰은 15분으로 짧아 무상태로 두어도 위험이 제한된다.
 */
@Component
public class RefreshTokenStore {

    private static final String TOKEN_KEY = "refresh:%d:%s";
    private static final String USER_INDEX_KEY = "refresh:user:%d";

    private final StringRedisTemplate redis;

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void store(Long userId, String jti, Duration ttl) {
        redis.opsForValue().set(tokenKey(userId, jti), "1", ttl);
        // 사용자별 인덱스 — 전 세션 무효화에 필요하다
        redis.opsForSet().add(userIndexKey(userId), jti);
        redis.expire(userIndexKey(userId), ttl);
    }

    public boolean isValid(Long userId, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(tokenKey(userId, jti)));
    }

    /** 회전: 기존 토큰을 폐기한다. 이후 같은 jti 로 오는 요청은 재사용으로 간주된다. */
    public void revoke(Long userId, String jti) {
        redis.delete(tokenKey(userId, jti));
        redis.opsForSet().remove(userIndexKey(userId), jti);
    }

    /** 재사용 감지 시 또는 명시적 전체 로그아웃 시 호출한다. */
    public void revokeAll(Long userId) {
        Set<String> jtis = redis.opsForSet().members(userIndexKey(userId));
        if (jtis != null && !jtis.isEmpty()) {
            redis.delete(jtis.stream().map(jti -> tokenKey(userId, jti)).toList());
        }
        redis.delete(userIndexKey(userId));
    }

    private String tokenKey(Long userId, String jti) {
        return TOKEN_KEY.formatted(userId, jti);
    }

    private String userIndexKey(Long userId) {
        return USER_INDEX_KEY.formatted(userId);
    }
}
