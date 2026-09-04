package com.litmood.interfaces.dto;

import com.litmood.domain.model.Content;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.Mood;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RecordDtos {

    private RecordDtos() {}

    @Schema(name = "CreateRecordRequest")
    public record CreateRecordRequest(
            @NotNull(message = "콘텐츠 제공자를 지정해 주세요") ProviderType provider,
            @NotBlank(message = "콘텐츠를 선택해 주세요") String externalId,
            // 필수 입력은 상태 하나뿐이다 — 기록의 마찰을 최소화한다 (F-03-01)
            @NotNull(message = "상태를 선택해 주세요") RecordStatus status,
            @DecimalMin(value = "0.5", message = "별점은 0.5 이상이어야 합니다")
                    @DecimalMax(value = "5.0", message = "별점은 5.0 이하여야 합니다")
                    BigDecimal rating,
            @Size(max = 5, message = "무드는 최대 5개까지 선택할 수 있습니다") List<@NotBlank @Size(max = 30) String> moods,
            @Size(max = 2000, message = "리뷰는 2000자를 넘을 수 없습니다") String review,
            Boolean isSpoiler,
            Visibility visibility,
            @Size(max = 200) String contextNote,
            LocalDate startedAt,
            LocalDate finishedAt,
            Integer repeatCount) {}

    @Schema(name = "UpdateRecordRequest")
    /**
     * 부분 수정. <b>넣지 않은 필드는 변경되지 않는다.</b>
     *
     * <p>지우려면: 문자열은 빈 문자열(""), 별점은 {@code clearRating: true}.
     * null 을 "지움"으로 쓰면 "변경 없음"을 표현할 수 없어 필드 하나만 고치는 요청이
     * 나머지를 날려버린다.
     */
    public record UpdateRecordRequest(
            RecordStatus status,
            @DecimalMin("0.5") @DecimalMax("5.0") BigDecimal rating,
            @Schema(description = "true 면 상태는 그대로 두고 별점만 지운다")
                    Boolean clearRating,
            @Size(max = 5, message = "무드는 최대 5개까지 선택할 수 있습니다") List<@NotBlank @Size(max = 30) String> moods,
            @Schema(description = "빈 문자열이면 지운다")
                    @Size(max = 2000)
                    String review,
            Boolean isSpoiler,
            Visibility visibility,
            @Schema(description = "빈 문자열이면 지운다") @Size(max = 200) String contextNote,
            LocalDate startedAt,
            LocalDate finishedAt,
            Integer repeatCount) {}

    @Schema(name = "MoodTag")
    public record MoodTag(String name, String displayName, String color, boolean curated) {
        public static MoodTag from(Mood mood) {
            return new MoodTag(mood.getName(), mood.getDisplayName(), mood.getColor(), mood.isCurated());
        }
    }

    @Schema(name = "ContentRef", description = "기록에 붙는 콘텐츠 요약")
    public record ContentRef(
            Long id,
            ContentType type,
            ProviderType provider,
            String externalId,
            String title,
            List<String> creators,
            LocalDate releasedOn,
            String coverUrl) {

        public static ContentRef from(Content content) {
            return new ContentRef(
                    content.getId(),
                    content.getType(),
                    content.getProvider(),
                    content.getExternalId(),
                    content.getTitle(),
                    content.getCreators(),
                    content.getReleasedOn(),
                    content.getCoverUrl());
        }
    }

    @Schema(name = "RecordResponse")
    public record RecordResponse(
            Long id,
            RecordStatus status,
            BigDecimal rating,
            List<MoodTag> moods,
            String review,
            boolean isSpoiler,
            Visibility visibility,
            String contextNote,
            LocalDate startedAt,
            LocalDate finishedAt,
            int repeatCount,
            int likeCount,
            boolean likedByMe,
            String authorHandle,
            String authorNickname,
            ContentRef content,
            Instant createdAt,
            Instant updatedAt) {

        /**
         * @param likedTargetIds 조회자가 좋아요를 누른 기록 id 집합.
         *     항목마다 개별 조회하면 N+1 이 되므로 목록 단위로 한 번에 판정해 넘긴다.
         */
        public static RecordResponse from(Record record, java.util.Set<Long> likedTargetIds) {
            return new RecordResponse(
                    record.getId(),
                    record.getStatus(),
                    record.getRating(),
                    record.getMoods().stream().map(MoodTag::from).toList(),
                    record.getReview(),
                    record.isSpoiler(),
                    record.getVisibility(),
                    record.getContextNote(),
                    record.getStartedAt(),
                    record.getFinishedAt(),
                    record.getRepeatCount(),
                    record.getLikeCount(),
                    likedTargetIds.contains(record.getId()),
                    record.getAuthor() == null ? null : record.getAuthor().getHandle(),
                    record.getAuthor() == null ? null : record.getAuthor().getNickname(),
                    ContentRef.from(record.getContent()),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }

    @Schema(name = "RecordPage", description = "커서 기반 페이지. nextCursor 가 null 이면 마지막 페이지다.")
    public record RecordPage(List<RecordResponse> items, String nextCursor, long totalCount) {}
}
