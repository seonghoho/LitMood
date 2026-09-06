package com.litmood.domain.repository;

import com.litmood.domain.model.Report;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReportRepository {

    Report save(Report report);

    boolean existsBy(Long reporterId, Report.ReportTarget targetType, Long targetId);

    Optional<Report> findById(Long id);

    /**
     * 처리 큐 (#28). {@code status} 가 null 이면 상태를 가리지 않는다.
     *
     * <p>정렬은 최신 접수 순이다 — {@code idx_reports_queue (status, created_at DESC)}
     * 가 그 전제로 만들어져 있고, 이 저장소의 다른 목록도 모두 같은 방향이다.
     * "오래 기다린 신고" 는 {@code pendingCount} 로 큐의 깊이를 보고 판단한다.
     * 커서는 {@code (createdAt, id)} 의 사전식 비교라, 목록을 보는 사이에 새 신고가
     * 들어와도 이미 본 페이지가 밀리지 않는다.
     */
    List<Report> findQueue(Report.ReportStatus status, Instant cursorCreatedAt, Long cursorId, int limit);

    long countByStatus(Report.ReportStatus status);

    /** 한 페이지에 실린 대상들에 신고가 몇 건씩 쌓였는지. 항목마다 세면 N+1 이다. */
    Map<Long, Long> countByTargets(Report.ReportTarget targetType, List<Long> targetIds);
}
