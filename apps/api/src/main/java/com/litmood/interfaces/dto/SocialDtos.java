package com.litmood.interfaces.dto;

import com.litmood.domain.model.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SocialDtos {

    private SocialDtos() {}

    @Schema(name = "FollowStatsResponse")
    public record FollowStatsResponse(long followers, long following, boolean followedByMe) {}

    @Schema(name = "LikeResponse")
    public record LikeResponse(int likeCount, boolean likedByMe) {}

    @Schema(name = "ReportRequest")
    public record ReportRequest(
            @NotNull(message = "신고 대상을 지정해 주세요") Report.ReportTarget targetType,
            @NotNull(message = "신고 대상을 지정해 주세요") Long targetId,
            @NotNull(message = "신고 사유를 선택해 주세요") Report.ReportReason reason,
            @Size(max = 500) String detail) {}
}
