package com.litmood.interfaces.controller;

import com.litmood.domain.repository.ContentRepository;
import com.litmood.infrastructure.redis.PopularityRanking;
import com.litmood.interfaces.dto.RecordDtos.ContentRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
    @Operation(summary = "인기 콘텐츠", description = "기간 내 기록 수 기준. Redis Sorted Set 으로 집계한다.")
    public List<ContentRef> popular(
            @RequestParam(required = false, defaultValue = "week") String period,
            @RequestParam(required = false, defaultValue = "20") int limit) {

        List<Long> ids = ranking.top(PopularityRanking.Period.from(period), Math.clamp(limit, 1, 50));

        // Redis 가 준 순서를 유지해야 한다 — DB 조회 결과 순서에 기대지 않는다
        return ids.stream()
                .map(contentRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(ContentRef::from)
                .toList();
    }
}
