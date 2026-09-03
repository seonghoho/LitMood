package com.litmood.interfaces.controller;

import com.litmood.domain.repository.MoodRepository;
import com.litmood.interfaces.dto.RecordDtos.MoodTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moods")
@Tag(name = "Moods", description = "무드")
public class MoodController {

    private final MoodRepository moodRepository;

    public MoodController(MoodRepository moodRepository) {
        this.moodRepository = moodRepository;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "무드 목록", description = "큐레이션 무드 우선, 그다음 사용량순")
    public List<MoodTag> list(@RequestParam(required = false, defaultValue = "30") int limit) {
        return moodRepository.findForPicker(Math.clamp(limit, 1, 100)).stream()
                .map(MoodTag::from)
                .toList();
    }
}
