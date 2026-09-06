package com.litmood.infrastructure.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * 인기 콘텐츠 랭킹 (F-07-02, ADR-005).
 *
 * RDB 로 하면 매 조회마다 records 를 기간으로 훑어 집계해야 한다.
 * Redis Sorted Set 은 기록이 생길 때 점수를 1 올리고, 조회는 상위 N개를
 * 그대로 읽는다 — 집계 비용이 조회 시점에서 쓰기 시점으로 옮겨간다.
 *
 * <p>랭킹은 부가 기능이므로 <b>실패해도 본 기능을 막지 않는다</b>.
 * Redis 장애 시 기록 생성은 정상 동작하고 랭킹만 비어 보인다.
 */
@Component
public class PopularityRanking {

    private static final Logger log = LoggerFactory.getLogger(PopularityRanking.class);

    // 기간 키가 지나면 자연히 사라지도록 TTL 을 넉넉히 준다
    private static final Duration WEEK_TTL = Duration.ofDays(21);
    private static final Duration MONTH_TTL = Duration.ofDays(75);

    private final StringRedisTemplate redis;

    public PopularityRanking(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 기록이 생성될 때 호출한다. */
    public void record(Long contentId) {
        try {
            LocalDate today = LocalDate.now();
            increment(weekKey(today), contentId, WEEK_TTL);
            increment(monthKey(today), contentId, MONTH_TTL);
        } catch (Exception e) {
            log.warn("인기 랭킹 갱신 실패 — 무시합니다: {}", e.getMessage());
        }
    }

    /**
     * 점수 높은 순. 결과가 비면 호출부가 빈 목록을 반환하면 된다.
     *
     * <p>점수(기록 수)를 함께 돌려준다 — 순위만 있고 근거가 없으면 화면에서
     * 그냥 콘텐츠 목록과 구분되지 않는다.
     */
    public List<Scored> top(Period period, int limit) {
        try {
            String key = period == Period.WEEK ? weekKey(LocalDate.now()) : monthKey(LocalDate.now());
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(key, 0, limit - 1L);

            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<Scored> scored = new ArrayList<>(tuples.size());
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null) {
                    // incrementScore 로만 쌓이므로 점수는 정수다
                    long count = tuple.getScore() == null ? 0L : Math.round(tuple.getScore());
                    scored.add(new Scored(Long.valueOf(tuple.getValue()), count));
                }
            }
            return scored;
        } catch (Exception e) {
            log.warn("인기 랭킹 조회 실패 — 빈 목록을 반환합니다: {}", e.getMessage());
            return List.of();
        }
    }

    /** 콘텐츠 id 와 그 기간의 기록 수. */
    public record Scored(Long contentId, long count) {}

    private void increment(String key, Long contentId, Duration ttl) {
        redis.opsForZSet().incrementScore(key, String.valueOf(contentId), 1);
        // 새로 만들어진 키에만 TTL 을 건다. 이미 있으면 남은 시간을 유지한다.
        if (Boolean.FALSE.equals(redis.hasKey(key))) {
            redis.expire(key, ttl);
        } else if (redis.getExpire(key) != null && redis.getExpire(key) < 0) {
            redis.expire(key, ttl);
        }
    }

    /** ISO 주 단위 — 연말연시에 주차가 뒤섞이지 않도록 weekBasedYear 를 쓴다. */
    private String weekKey(LocalDate date) {
        WeekFields iso = WeekFields.ISO;
        return "popular:week:%d-%02d"
                .formatted(date.get(iso.weekBasedYear()), date.get(iso.weekOfWeekBasedYear()));
    }

    private String monthKey(LocalDate date) {
        return "popular:month:%d-%02d".formatted(date.getYear(), date.getMonthValue());
    }

    public enum Period {
        WEEK,
        MONTH;

        public static Period from(String raw) {
            if (raw == null) {
                return WEEK;
            }
            return "month".equalsIgnoreCase(raw.trim()) ? MONTH : WEEK;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
