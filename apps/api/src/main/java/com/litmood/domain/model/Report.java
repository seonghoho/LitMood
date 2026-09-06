package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;

/** 신고 (F-06-05). 운영자가 처리할 큐에 쌓인다. */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Report() {}

    public static Report of(
            Long reporterId, ReportTarget targetType, Long targetId, ReportReason reason, String detail) {
        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetId = targetId;
        report.reason = reason;
        report.detail = detail;
        return report;
    }

    /**
     * 불변식 — 신고는 한 번만 처리된다.
     *
     * <p>이미 처리된 건을 다시 뒤집으면 {@code resolvedAt} 이 "언제 판단했는가" 를
     * 더 이상 말해 주지 못한다. 판단을 바꿔야 할 만큼의 일이라면 흔적이 남는 별도
     * 절차가 필요하지, 같은 버튼으로 덮어쓸 일이 아니다.
     * DB 에도 같은 규칙이 CHECK 제약으로 걸려 있다 (V4__report_resolution.sql).
     */
    public void resolve(ReportStatus decision) {
        if (decision == null || decision == ReportStatus.PENDING) {
            throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "처리 결과는 REVIEWED 또는 DISMISSED 여야 합니다");
        }
        if (status != ReportStatus.PENDING) {
            throw new LitmoodException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        this.status = decision;
        this.resolvedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public ReportTarget getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public enum ReportTarget {
        RECORD,
        COLLECTION,
        USER,
    }

    public enum ReportReason {
        SPAM,
        ABUSE,
        SEXUAL,
        SPOILER,
        COPYRIGHT,
        OTHER,
    }

    public enum ReportStatus {
        PENDING,
        REVIEWED,
        DISMISSED,
    }
}
