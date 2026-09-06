package com.litmood.interfaces.controller;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.Content;
import com.litmood.domain.repository.ContentRepository;
import com.litmood.infrastructure.redis.PopularityRanking;
import com.litmood.interfaces.dto.RecordDtos.ContentRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** F-07-02 — 인기 콘텐츠. */
@RestController
@RequestMapping("/api/v1/discover")
@Tag(name = "Discover", description = "탐색")
@SecurityRequirements // 비로그인 사용자의 유입 경로다
public class DiscoverController {

    private final PopularityRanking ranking;
    private final ContentRepository contentRepository;

    public DiscoverController(PopularityRanking ranking, ContentRepository contentRepository) {
        this.ranking = ranking;
        this.contentRepository = contentRepository;
    }

    @GetMapping("/popular")
    @Operation(
            summary = "인기 콘텐츠",
            description = "기간 내 기록 수 기준. Redis Sorted Set 으로 집계한다."
                    + " period 는 week(기본) 또는 month 이고, 그 밖의 값은 week 로 본다.")
    public List<PopularContent> popular(
            @RequestParam(required = false, defaultValue = "week") String period,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<PopularityRanking.Scored> scored =
                ranking.top(PopularityRanking.Period.from(period), Math.clamp(limit, 1, 50));
        if (scored.isEmpty()) {
            return List.of();
        }

        // 콘텐츠는 한 번에 조회한다 — 하나씩 돌면 랭킹 크기만큼 쿼리가 나간다
        Map<Long, Content> byId = contentRepository
                .findAllById(scored.stream().map(PopularityRanking.Scored::contentId).toList())
                .stream()
                .collect(Collectors.toMap(Content::getId, Function.identity()));

        // Redis 가 준 순서를 유지해야 한다 — DB 조회 결과 순서에 기대지 않는다
        return scored.stream()
                .map(entry -> {
                    Content content = byId.get(entry.contentId());
                    return content == null ? null : new PopularContent(ContentRef.from(content), entry.count());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 무드 탐색의 RankedContent 와 모양이 비슷하지만 합치지 않았다 —
     * 그쪽은 DB 집계라 평균 별점을 함께 낼 수 있고, 이쪽은 Redis 카운터라 낼 수 없다.
     * 한 타입으로 묶으면 영구히 null 인 필드를 계약에 남기게 된다.
     */
    @Schema(name = "PopularContent", description = "기간 내 기록 수 기준 인기 콘텐츠")
    public record PopularContent(
            @Schema(requiredMode = REQUIRED) ContentRef content,
            @Schema(requiredMode = REQUIRED) long recordCount) {}
}
