package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ContentDtos {

    private ContentDtos() {}

    @Schema(name = "ContentSummary", description = "정규화된 콘텐츠. provider 차이는 서버가 흡수한다.")
    public record ContentSummary(
            @Schema(requiredMode = REQUIRED) ContentType type,
            @Schema(requiredMode = REQUIRED) ProviderType provider,
            @Schema(requiredMode = REQUIRED) String externalId,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED) List<String> creators,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) LocalDate releasedOn,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String coverUrl,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String description,
            @Schema(requiredMode = REQUIRED) Map<String, Object> metadata) {

        public static ContentSummary from(ContentSnapshot snapshot) {
            return new ContentSummary(
                    snapshot.type(),
                    snapshot.provider(),
                    snapshot.externalId(),
                    snapshot.title(),
                    snapshot.creators(),
                    snapshot.releasedOn(),
                    snapshot.coverUrl(),
                    snapshot.description(),
                    snapshot.metadata());
        }
    }

    @Schema(
            name = "SearchResponse",
            description = "부분 실패를 허용한다. 실패한 provider 는 failedProviders 로 알린다 (NFR-03).")
    public record SearchResponse(
            @Schema(requiredMode = REQUIRED) Map<ContentType, List<ContentSummary>> results,
            @Schema(requiredMode = REQUIRED) List<ProviderType> failedProviders,
            @Schema(requiredMode = REQUIRED) boolean cached) {}
}
