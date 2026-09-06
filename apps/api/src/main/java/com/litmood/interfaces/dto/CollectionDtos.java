package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.Collection;
import com.litmood.domain.model.CollectionItem;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.Visibility;
import com.litmood.interfaces.dto.RecordDtos.ContentRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CollectionDtos {

    private CollectionDtos() {}

    @Schema(name = "CreateCollectionRequest")
    public record CreateCollectionRequest(
            @NotBlank(message = "제목을 입력해 주세요") @Size(max = 100) String title,
            @Size(max = 500) String description,
            Visibility visibility) {}

    @Schema(name = "UpdateCollectionRequest")
    public record UpdateCollectionRequest(
            @Size(max = 100) String title,
            @Size(max = 500) String description,
            String coverUrl,
            Visibility visibility) {}

    @Schema(name = "AddCollectionItemRequest")
    public record AddCollectionItemRequest(
            @NotNull(message = "콘텐츠 제공자를 지정해 주세요") ProviderType provider,
            @NotBlank(message = "콘텐츠를 선택해 주세요") String externalId,
            @Size(max = 300) String note) {}

    @Schema(name = "ReorderItemsRequest", description = "전달된 순서대로 position 을 다시 매긴다")
    public record ReorderItemsRequest(@NotEmpty(message = "순서를 지정해 주세요") List<Long> contentIds) {}

    @Schema(name = "CollectionItemResponse")
    public record CollectionItemResponse(
            @Schema(requiredMode = REQUIRED) ContentRef content,
            @Schema(requiredMode = REQUIRED) int position,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String note) {
        static CollectionItemResponse from(CollectionItem item) {
            return new CollectionItemResponse(
                    ContentRef.from(item.getContent()), item.getPosition(), item.getNote());
        }
    }

    @Schema(name = "CollectionSummary", description = "목록용 — 아이템 없이 개수만")
    public record CollectionSummary(
            @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String description,
            // 표지를 직접 지정하지 않으면 첫 아이템에서 끌어오고, 그것도 없으면 null 이다
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String coverUrl,
            @Schema(requiredMode = REQUIRED) Visibility visibility,
            @Schema(requiredMode = REQUIRED) int itemCount,
            @Schema(requiredMode = REQUIRED) int likeCount,
            @Schema(requiredMode = REQUIRED) Instant createdAt) {

        // 목록에는 likedByMe 를 싣지 않는다. 이 목록이 나가는 공개 프로필은 인증 없이
        // SSR 되고 캐시되므로, 조회자별 값을 실으면 한 사람의 상태가 캐시로 새어 나간다.
        public static CollectionSummary from(Collection collection) {
            return new CollectionSummary(
                    collection.getSlug(),
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.resolveCoverUrl(),
                    collection.getVisibility(),
                    collection.getItemCount(),
                    collection.getLikeCount(),
                    collection.getCreatedAt());
        }
    }

    @Schema(name = "CollectionResponse")
    public record CollectionResponse(
            @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String description,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String coverUrl,
            @Schema(requiredMode = REQUIRED) Visibility visibility,
            @Schema(requiredMode = REQUIRED) int itemCount,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String ownerHandle,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String ownerNickname,
            @Schema(requiredMode = REQUIRED) int likeCount,
            @Schema(requiredMode = REQUIRED) boolean likedByMe,
            @Schema(requiredMode = REQUIRED) List<CollectionItemResponse> items,
            @Schema(requiredMode = REQUIRED) Instant createdAt,
            @Schema(requiredMode = REQUIRED) Instant updatedAt) {

        /**
         * @param likedByMe 조회자가 이 컬렉션에 좋아요를 눌렀는지. 비로그인 조회에서는 언제나 false 다 —
         *     이 응답은 캐시되므로 호출부가 조회자별 값을 캐시에 실어 보내지 않도록 주의한다
         *     (docs/03-architecture.md "캐싱과 개인화의 경계").
         */
        public static CollectionResponse from(
                Collection collection, String ownerHandle, String ownerNickname, boolean likedByMe) {
            return new CollectionResponse(
                    collection.getSlug(),
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.resolveCoverUrl(),
                    collection.getVisibility(),
                    collection.getItemCount(),
                    ownerHandle,
                    ownerNickname,
                    collection.getLikeCount(),
                    likedByMe,
                    collection.getItems().stream().map(CollectionItemResponse::from).toList(),
                    collection.getCreatedAt(),
                    collection.getUpdatedAt());
        }
    }
}
