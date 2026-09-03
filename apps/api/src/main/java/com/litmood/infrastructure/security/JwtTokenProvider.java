package com.litmood.infrastructure.security;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.infrastructure.config.LitmoodProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** ADR-009 — Access 토큰 발급·검증. Refresh 는 {@link RefreshTokenStore} 가 담당한다. */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_HANDLE = "handle";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(LitmoodProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // 약한 시크릿으로 조용히 기동하면 운영에서 위조가 가능해진다. 기동 자체를 막는다.
            throw new IllegalStateException(
                    "JWT_SECRET 이 없거나 32바이트 미만입니다. `openssl rand -base64 48` 로 생성해 주입하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofSeconds(properties.jwt().accessTtlSeconds());
        this.refreshTtl = Duration.ofSeconds(properties.jwt().refreshTtlSeconds());
    }

    public String createAccessToken(Long userId, String handle) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_HANDLE, handle)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Refresh 토큰. jti 를 담아 Redis 화이트리스트와 대조하고 회전 시 폐기한다.
     * 토큰 자체에 권한 정보를 넣지 않는다 — 재발급 용도로만 쓰인다.
     */
    public RefreshToken createRefreshToken(Long userId) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTtl)))
                .signWith(key)
                .compact();
        return new RefreshToken(token, jti, refreshTtl);
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new LitmoodException(ErrorCode.UNAUTHORIZED, "유효하지 않은 토큰입니다");
        }
    }

    public Long userIdOf(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String handleOf(Claims claims) {
        return claims.get(CLAIM_HANDLE, String.class);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public record RefreshToken(String value, String jti, Duration ttl) {}
}
