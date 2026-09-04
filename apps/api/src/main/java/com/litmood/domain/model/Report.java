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

    public Long getId() {
        return id;
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
