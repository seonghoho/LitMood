package com.litmood.interfaces.controller;

import com.litmood.domain.model.Mood;
import com.litmood.domain.repository.MoodDiscoveryRepository;
import com.litmood.domain.repository.MoodRepository;
import com.litmood.interfaces.dto.RecordDtos.ContentRef;
import com.litmood.interfaces.dto.RecordDtos.MoodTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moods")
@Tag(name = "Moods", description = "무드")
@SecurityRequirements // 탐색은 비로그인 사용자에게도 열려 있다 (유입 경로)
public class MoodController {

    private final MoodRepository moodRepository;
    private final MoodDiscoveryRepository discoveryRepository;

    public MoodController(MoodRepository moodRepository, MoodDiscoveryRepository discoveryRepository) {
        this.moodRepository = moodRepository;
        this.discoveryRepository = discoveryRepository;
    }

    @GetMapping
    @Operation(summary = "무드 목록", description = "큐레이션 무드 우선, 그다음 사용량순")
    public List<MoodTag> list(@RequestParam(required = false, defaultValue = "30") int limit) {
        return moodRepository.findForPicker(Math.clamp(limit, 1, 100)).stream()
                .map(MoodTag::from)
                .toList();
    }

    @GetMapping("/{name}/contents")
    @Operation(
            summary = "무드별 콘텐츠 랭킹",
            description = "해당 무드로 기록된 공개 기록 수 기준. 이름은 정규화되어 비교된다.")
    public MoodDiscovery contentsByMood(
            @PathVariable String name, @RequestParam(required = false, defaultValue = "20") int limit) {

        String normalized = Mood.normalize(name);
        MoodTag mood = moodRepository
                .findByName(normalized)
                .map(MoodTag::from)
                // 아직 아무도 쓰지 않은 무드도 빈 결과로 응답한다 — 404 는 과하다
                .orElse(new MoodTag(normalized, name.trim(), null, false));

        List<RankedContent> contents = discoveryRepository
                .rankByMood(normalized, Math.clamp(limit, 1, 50))
                .stream()
                .map(r -> new RankedContent(
                        ContentRef.from(r.content()), r.recordCount(), round(r.averageRating())))
                .toList();

        return new MoodDiscovery(mood, contents);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    @Schema(name = "RankedContent")
    public record RankedContent(ContentRef content, long recordCount, Double averageRating) {}

    @Schema(name = "MoodDiscovery")
    public record MoodDiscovery(MoodTag mood, List<RankedContent> contents) {}
}
