package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 운영 화면 (#28). 신고 큐를 보고 처리한다. */
public final class AdminDtos {

    private AdminDtos() {}

    /**
     * 신고 대상을 사람이 읽을 수 있게 편 것.
     *
     * <p>{@code targetType} + {@code targetId} 만 주면 운영자가 대상마다 다시 조회해야
     * 한다. 목록을 만들 때 한 번에 채워 보낸다.
     */
    @Schema(name = "AdminReportTarget")
    public record AdminReportTarget(
            @Schema(requiredMode = REQUIRED) Report.ReportTarget type,
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(
                            requiredMode = REQUIRED,
                            types = {"string", "null"},
                            description = "기록은 콘텐츠 제목, 컬렉션은 제목, 사용자는 닉네임. 삭제된 대상이면 null")
                    String label,
            @Schema(
                            requiredMode = REQUIRED,
                            types = {"string", "null"},
                            description = "대상 본인 또는 대상을 만든 사람의 핸들. 프로필로 이동하는 데 쓴다")
                    String handle,
            @Schema(
                            requiredMode = REQUIRED,
                            types = {"string", "null"},
                            description = "컬렉션일 때만 채워진다")
                    String slug,
            @Schema(requiredMode = REQUIRED, description = "이미 지워진 대상이면 true — 조치할 것이 남아 있지 않다")
                    boolean deleted) {}

    @Schema(name = "AdminReportResponse")
    public record AdminReportResponse(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) Report.ReportReason reason,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String detail,
            @Schema(requiredMode = REQUIRED) Report.ReportStatus status,
            @Schema(requiredMode = REQUIRED) AdminReportTarget target,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}, description = "신고자 핸들. 탈퇴했으면 null")
                    String reporterHandle,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String reporterNickname,
            @Schema(
                            requiredMode = REQUIRED,
                            description = "같은 대상에 쌓인 신고 건수(상태 무관). 여러 사람이 같은 것을 신고했다는 신호다")
                    long sameTargetCount,
            @Schema(requiredMode = REQUIRED) Instant createdAt,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) Instant resolvedAt) {}

    @Schema(name = "AdminReportPage", description = "커서 기반 페이지. nextCursor 가 null 이면 마지막 페이지다.")
    public record AdminReportPage(
            @Schema(requiredMode = REQUIRED) List<AdminReportResponse> items,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String nextCursor,
            @Schema(requiredMode = REQUIRED, description = "필터에 걸린 전체 건수") long totalCount,
            @Schema(requiredMode = REQUIRED, description = "아직 처리하지 않은 신고 수. 필터와 무관하게 큐의 크기를 알려준다")
                    long pendingCount) {}

    @Schema(name = "ReportResolutionRequest", description = "신고 처리 결과. PENDING 으로는 되돌릴 수 없다")
    public record ReportResolutionRequest(
            @NotNull(message = "처리 결과를 선택해 주세요") Report.ReportStatus status) {}
}
