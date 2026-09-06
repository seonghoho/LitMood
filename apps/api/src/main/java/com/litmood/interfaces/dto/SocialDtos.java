package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class SocialDtos {

    private SocialDtos() {}

    @Schema(name = "FollowStatsResponse")
    public record FollowStatsResponse(
            @Schema(requiredMode = REQUIRED) long followers,
            @Schema(requiredMode = REQUIRED) long following,
            @Schema(requiredMode = REQUIRED) boolean followedByMe) {}

    @Schema(name = "LikeResponse")
    public record LikeResponse(
            @Schema(requiredMode = REQUIRED) int likeCount,
            @Schema(requiredMode = REQUIRED) boolean likedByMe) {}

    @Schema(name = "BlockedUserResponse", description = "내가 차단한 사용자")
    public record BlockedUserResponse(
            @Schema(requiredMode = REQUIRED) String handle,
            @Schema(requiredMode = REQUIRED) String nickname,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String avatarUrl,
            @Schema(requiredMode = REQUIRED) Instant blockedAt) {}

    @Schema(name = "ReportRequest", description = "신고 대상은 경로가 가리킨다 — 본문에는 사유만 담는다")
    public record ReportRequest(
            @NotNull(message = "신고 사유를 선택해 주세요") Report.ReportReason reason,
            @Size(max = 500) String detail) {}
}
