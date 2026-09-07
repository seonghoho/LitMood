package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Report;
import com.litmood.domain.model.Report.ReportReason;
import com.litmood.domain.model.Report.ReportStatus;
import com.litmood.domain.model.Report.ReportTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신고 처리 불변식 (#28). 스프링 컨텍스트 없이 엔티티만 검증한다. */
class ReportDomainTest {

    private Report pending() {
        return Report.of(1L, ReportTarget.RECORD, 10L, ReportReason.SPAM, "광고입니다");
    }

    @Test
    @DisplayName("처리하면 상태와 처리 시각이 함께 남는다")
    void resolveRecordsDecisionAndTime() {
        Report report = pending();

        report.resolve(ReportStatus.REVIEWED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        assertThat(report.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 처리된 신고는 다시 처리하지 않는다 — resolvedAt 이 언제 판단했는지를 잃는다")
    void resolveOnlyOnce() {
        Report report = pending();
        report.resolve(ReportStatus.DISMISSED);

        assertThatThrownBy(() -> report.resolve(ReportStatus.REVIEWED))
                .isInstanceOf(LitmoodException.class)
                .extracting(e -> ((LitmoodException) e).errorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_RESOLVED);
    }

    @Test
    @DisplayName("PENDING 으로는 되돌릴 수 없다 — 처리 결과가 아니라 미처리 상태다")
    void cannotResolveToPending() {
        assertThatThrownBy(() -> pending().resolve(ReportStatus.PENDING))
                .isInstanceOf(LitmoodException.class)
                .extracting(e -> ((LitmoodException) e).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("접수 직후에는 처리 시각이 없다")
    void newReportIsPending() {
        Report report = pending();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(report.getResolvedAt()).isNull();
    }
}
