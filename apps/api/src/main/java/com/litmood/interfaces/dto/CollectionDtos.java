package com.litmood.interfaces.dto;

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
    public record CollectionItemResponse(ContentRef content, int position, String note) {
        static CollectionItemResponse from(CollectionItem item) {
            return new CollectionItemResponse(
                    ContentRef.from(item.getContent()), item.getPosition(), item.getNote());
        }
    }

    @Schema(name = "CollectionSummary", description = "목록용 — 아이템 없이 개수만")
    public record CollectionSummary(
            String slug,
            String title,
            String description,
            String coverUrl,
            Visibility visibility,
            int itemCount,
            Instant createdAt) {

        public static CollectionSummary from(Collection collection) {
            return new CollectionSummary(
                    collection.getSlug(),
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.resolveCoverUrl(),
                    collection.getVisibility(),
                    collection.getItemCount(),
                    collection.getCreatedAt());
        }
    }

    @Schema(name = "CollectionResponse")
    public record CollectionResponse(
            String slug,
            String title,
            String description,
            String coverUrl,
            Visibility visibility,
            int itemCount,
            String ownerHandle,
            String ownerNickname,
            List<CollectionItemResponse> items,
            Instant createdAt,
            Instant updatedAt) {

        public static CollectionResponse from(Collection collection, String ownerHandle, String ownerNickname) {
            return new CollectionResponse(
                    collection.getSlug(),
                    collection.getTitle(),
                    collection.getDescription(),
                    collection.resolveCoverUrl(),
                    collection.getVisibility(),
                    collection.getItemCount(),
                    ownerHandle,
                    ownerNickname,
                    collection.getItems().stream().map(CollectionItemResponse::from).toList(),
                    collection.getCreatedAt(),
                    collection.getUpdatedAt());
        }
    }
}
