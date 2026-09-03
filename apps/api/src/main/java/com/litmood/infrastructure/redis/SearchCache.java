package com.litmood.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litmood.domain.model.ContentType;
import com.litmood.infrastructure.config.LitmoodProperties;
import com.litmood.interfaces.dto.ContentDtos.ContentSummary;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 외부 검색 결과 캐시 (F-02-04, ADR-005).
 *
 * 캐시는 부가 기능이므로 <b>실패해도 검색을 막지 않는다</b> — Redis 장애 시
 * 조용히 캐시 미스로 처리하고 provider 를 직접 호출한다.
 */
@Component
public class SearchCache {

    private static final Logger log = LoggerFactory.getLogger(SearchCache.class);
    private static final TypeReference<List<ContentSummary>> LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public SearchCache(StringRedisTemplate redis, ObjectMapper objectMapper, LitmoodProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(properties.cache().searchTtlHours());
    }

    public Optional<List<ContentSummary>> get(ContentType type, String query, int limit) {
        try {
            String cached = redis.opsForValue().get(key(type, query, limit));
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, LIST_TYPE));
        } catch (Exception e) {
            log.warn("검색 캐시 조회 실패 — 미스로 처리합니다: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(ContentType type, String query, int limit, List<ContentSummary> items) {
        try {
            redis.opsForValue().set(key(type, query, limit), objectMapper.writeValueAsString(items), ttl);
        } catch (Exception e) {
            log.warn("검색 캐시 저장 실패 — 무시합니다: {}", e.getMessage());
        }
    }

    /**
     * 검색어를 그대로 키에 넣으면 공백·콜론·한글 인코딩 때문에 키가 깨진다.
     * 정규화 후 Base64(URL-safe)로 감싼다.
     */
    private String key(ContentType type, String query, int limit) {
        String normalized = query.trim().toLowerCase(Locale.KOREAN).replaceAll("\\s+", " ");
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return "search:%s:%d:%s".formatted(type.name(), limit, encoded);
    }
}
