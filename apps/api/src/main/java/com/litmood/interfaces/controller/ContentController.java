package com.litmood.interfaces.controller;

import com.litmood.application.service.ContentSearchService;
import com.litmood.domain.model.ContentType;
import com.litmood.interfaces.dto.ContentDtos.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contents")
@Tag(name = "Contents", description = "콘텐츠 검색·조회")
@Validated
public class ContentController {

    private final ContentSearchService searchService;

    public ContentController(ContentSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @SecurityRequirements // 비로그인 사용자도 검색할 수 있다 (탐색형 유입)
    @Operation(
            summary = "통합 검색",
            description = "책·영화·음악을 동시에 검색한다. 일부 provider 가 실패해도 나머지 결과를 반환한다.")
    public SearchResponse search(
            @RequestParam @NotBlank(message = "검색어를 입력해 주세요") @Size(max = 100) String q,
            @RequestParam(required = false) List<ContentType> types,
            @RequestParam(required = false) Integer limit) {
        return searchService.search(q, types, limit);
    }
}
