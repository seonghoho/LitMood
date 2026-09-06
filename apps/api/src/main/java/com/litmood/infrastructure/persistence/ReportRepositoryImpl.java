package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Report;
import com.litmood.domain.repository.ReportRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

interface ReportJpaRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, Report.ReportTarget targetType, Long targetId);

    /*
     * 선택적 조건을 파라미터의 null 검사로 접지 않는다.
     *   - "상태를 가리지 않음" 은 전체 목록을 IN 으로 넘긴다.
     *   - 첫 페이지는 커서 조건이 아예 없는 별도 질의다.
     * `:param IS NULL` 로 묶으면 Postgres 가 바인딩 타입을 정하지 못해
     * ("could not determine data type of parameter") 기동이 아니라 첫 호출에서 터진다.
     */
    @Query(
            """
            SELECT r FROM Report r
            WHERE r.status IN :statuses
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Report> findQueueFirstPage(@Param("statuses") List<Report.ReportStatus> statuses, Limit limit);

    @Query(
            """
            SELECT r FROM Report r
            WHERE r.status IN :statuses
              AND (r.createdAt < :cursorCreatedAt
                   OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId))
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Report> findQueueAfter(
            @Param("statuses") List<Report.ReportStatus> statuses,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Limit limit);

    long countByStatusIn(List<Report.ReportStatus> statuses);

    @Query(
            """
            SELECT r.targetId, COUNT(r) FROM Report r
            WHERE r.targetType = :targetType AND r.targetId IN :targetIds
            GROUP BY r.targetId
            """)
    List<Object[]> countByTargets(
            @Param("targetType") Report.ReportTarget targetType, @Param("targetIds") List<Long> targetIds);
}

@Repository
class ReportRepositoryImpl implements ReportRepository {

    private final ReportJpaRepository jpa;

    ReportRepositoryImpl(ReportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Report save(Report report) {
        return jpa.save(report);
    }

    @Override
    public boolean existsBy(Long reporterId, Report.ReportTarget targetType, Long targetId) {
        return jpa.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId);
    }

    @Override
    public Optional<Report> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Report> findQueue(
            Report.ReportStatus status, Instant cursorCreatedAt, Long cursorId, int limit) {
        List<Report.ReportStatus> statuses = statuses(status);
        return cursorCreatedAt == null
                ? jpa.findQueueFirstPage(statuses, Limit.of(limit))
                : jpa.findQueueAfter(statuses, cursorCreatedAt, cursorId, Limit.of(limit));
    }

    @Override
    public long countByStatus(Report.ReportStatus status) {
        return jpa.countByStatusIn(statuses(status));
    }

    private static List<Report.ReportStatus> statuses(Report.ReportStatus status) {
        return status == null ? List.of(Report.ReportStatus.values()) : List.of(status);
    }

    @Override
    public Map<Long, Long> countByTargets(Report.ReportTarget targetType, List<Long> targetIds) {
        // IN () 은 SQL 문법 오류다. 빈 목록은 질의 없이 끝낸다.
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : jpa.countByTargets(targetType, targetIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
