package com.litmood.domain.repository;

import com.litmood.domain.model.Record;
import java.util.List;
import java.util.Optional;

public interface RecordRepository {

    Record save(Record record);

    Optional<Record> findActiveById(Long id);

    /** 불변식 1 — 한 사용자 · 한 콘텐츠 · 한 기록. */
    Optional<Record> findActiveByUserAndContent(Long userId, Long contentId);

    /** 커서 기반 타임라인 조회 (F-04-01·02). */
    List<Record> findTimeline(RecordQuery query);

    long countTimeline(RecordQuery query);
}
