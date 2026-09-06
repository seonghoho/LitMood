package com.litmood.domain.repository;

import com.litmood.domain.model.Record;
import java.util.List;
import java.util.Optional;

public interface RecordRepository {

    Record save(Record record);

    Optional<Record> findActiveById(Long id);

    /** 불변식 1 — 한 사용자 · 한 콘텐츠 · 한 기록. */
    Optional<Record> findActiveByUserAndContent(Long userId, Long contentId);

    /**
     * 여러 콘텐츠에 대한 내 기록을 한 번에 (#11).
     *
     * <p>검색 결과에서 "이미 기록한 것"을 가려내려면 결과 수만큼 조회가 필요한데,
     * 한 건씩 돌면 화면 하나에 스무 번이 나간다.
     */
    List<Record> findActiveByUserAndContents(Long userId, List<Long> contentIds);

    /** 여러 기록을 한 번에. 삭제된 것도 포함한다 (신고 큐 #28). */
    List<Record> findAllByIds(List<Long> ids);

    /** 커서 기반 타임라인 조회 (F-04-01·02). */
    List<Record> findTimeline(RecordQuery query);

    long countTimeline(RecordQuery query);
}
