package com.litmood.interfaces.controller;

import com.litmood.application.service.RecordService;
import com.litmood.application.service.RecordService.TimelineFilter;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.RecordStatus;
import com.litmood.infrastructure.security.AuthPrincipal;
import com.litmood.infrastructure.security.CurrentUser;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
import com.litmood.interfaces.dto.RecordDtos.RecordResponse;
import com.litmood.interfaces.dto.RecordDtos.UpdateRecordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/records")
@Tag(name = "Records", description = "기록")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @PostMapping
    @Operation(
            summary = "기록 생성",
            description = "콘텐츠가 자체 DB 에 없으면 provider 에서 가져와 스냅샷을 만든다. 필수 입력은 status 뿐이다.")
    public ResponseEntity<RecordResponse> create(
            @CurrentUser AuthPrincipal principal, @Valid @RequestBody CreateRecordRequest request) {
        RecordResponse created = recordService.create(principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/records/" + created.id())).body(created);
    }

    @GetMapping("/me")
    @Operation(summary = "내 타임라인", description = "커서 기반 페이지네이션")
    public RecordPage myTimeline(
            @CurrentUser AuthPrincipal principal,
            @RequestParam(required = false) List<ContentType> types,
            @RequestParam(required = false) List<RecordStatus> status,
            @RequestParam(required = false) List<String> moods,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        return recordService.timeline(
                principal.userId(),
                new TimelineFilter(types, status, moods, minRating, from, to),
                cursor,
                limit);
    }

    @GetMapping("/feed")
    @Operation(summary = "팔로잉 피드", description = "팔로우한 사용자들의 공개·팔로워 공개 기록. 차단한 사용자는 제외된다.")
    public RecordPage feed(
            @CurrentUser AuthPrincipal principal,
            @RequestParam(required = false) List<ContentType> types,
            @RequestParam(required = false) List<String> moods,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {

        return recordService.feed(
                principal.userId(),
                new TimelineFilter(types, null, moods, null, null, null),
                cursor,
                limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "기록 단건 조회", description = "공개 범위를 벗어나면 404 로 응답한다")
    public RecordResponse get(@CurrentUser AuthPrincipal principal, @PathVariable Long id) {
        return recordService.get(principal == null ? null : principal.userId(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "기록 수정")
    public RecordResponse update(
            @CurrentUser AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecordRequest request) {
        return recordService.update(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "기록 삭제", description = "soft delete")
    public ResponseEntity<Void> delete(@CurrentUser AuthPrincipal principal, @PathVariable Long id) {
        recordService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
