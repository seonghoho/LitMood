package com.litmood.domain.repository;

import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Visibility;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 타임라인·피드 조회 조건 (F-04-02, F-06-02).
 *
 * @param ownerIds 조회 대상 사용자들 — 내 타임라인이면 1명, 피드면 팔로잉 전체
 * @param visibleTo 허용할 공개 범위 — 본인이면 전체, 타인이면 PUBLIC(+FOLLOWERS)
 * @param excludedUserIds 차단으로 가려야 할 사용자 (F-06-05)
 * @param cursorCreatedAt 커서: 이 시각보다 과거인 기록만
 * @param cursorId 같은 시각일 때의 타이브레이커
 */
public record RecordQuery(
        Collection<Long> ownerIds,
        List<Visibility> visibleTo,
        Set<Long> excludedUserIds,
        List<ContentType> types,
        List<RecordStatus> statuses,
        List<String> moodNames,
        BigDecimal minRating,
        LocalDate from,
        LocalDate to,
        Instant cursorCreatedAt,
        Long cursorId,
        int limit) {

    /** 단일 사용자 타임라인용 축약 생성자. */
    public static RecordQuery forOwner(
            Long ownerId,
            List<Visibility> visibleTo,
            List<ContentType> types,
            List<RecordStatus> statuses,
            List<String> moodNames,
            BigDecimal minRating,
            LocalDate from,
            LocalDate to,
            Instant cursorCreatedAt,
            Long cursorId,
            int limit) {
        return new RecordQuery(
                List.of(ownerId), visibleTo, Set.of(), types, statuses, moodNames,
                minRating, from, to, cursorCreatedAt, cursorId, limit);
    }
}
