package com.litmood.domain.repository;

import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Visibility;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 타임라인 조회 조건 (F-04-02).
 *
 * @param ownerId 조회 대상 사용자
 * @param visibleTo 허용할 공개 범위 — 본인 조회면 전체, 타인 조회면 PUBLIC(+FOLLOWERS)
 * @param cursorCreatedAt 커서: 이 시각보다 과거인 기록만
 * @param cursorId 같은 시각일 때의 타이브레이커
 */
public record RecordQuery(
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
        int limit) {}
