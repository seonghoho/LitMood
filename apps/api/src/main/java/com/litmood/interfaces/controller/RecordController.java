package com.litmood.interfaces.controller;

import com.litmood.application.service.RecordService;
import com.litmood.application.service.RecordService.ContentKey;
import com.litmood.application.service.RecordService.TimelineFilter;
import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
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

    /** 검색 결과 한 화면보다 넉넉하되, 목록 전체를 훑는 데 쓰이지는 않을 만큼. */
    private static final int MAX_REFS = 50;

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

    @GetMapping("/me/by-content")
    @Operation(
            summary = "콘텐츠로 내 기록 찾기",
            description =
                    """
                    검색 결과 중 이미 기록한 것을 가려낸다 (F-03-01). refs 는 `PROVIDER:externalId` 형식으로 여러 번 준다.
                    기록이 없는 콘텐츠는 응답에서 빠지므로, 없다고 404 가 나지는 않는다.
                    검색 응답에 실을 수 없는 정보다 — 검색 결과는 캐시되므로 사용자별 값을 섞으면 다른 사람에게 샌다.
                    """)
    public List<RecordResponse> myRecordsByContent(
            @CurrentUser AuthPrincipal principal, @RequestParam(required = false) List<String> refs) {

        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        if (refs.size() > MAX_REFS) {
            throw new LitmoodException(
                    ErrorCode.VALIDATION_FAILED, "한 번에 " + MAX_REFS + "개까지 조회할 수 있습니다");
        }
        return recordService.findMineByContents(
                principal.userId(), refs.stream().map(RecordController::parseRef).toList());
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

    /**
     * {@code "NAVER_BOOK:9788937460449"} → {@link ContentKey}.
     *
     * <p>구분자는 <b>처음 나오는 것 하나만</b> 쓴다. provider 이름에는 콜론이 없지만
     * externalId 에는 있을 수 있다(예: {@code spotify:track:...} 형태를 쓰는 provider 가 붙는 경우).
     */
    private static ContentKey parseRef(String raw) {
        int separator = raw == null ? -1 : raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            throw new LitmoodException(
                    ErrorCode.VALIDATION_FAILED, "콘텐츠 지정은 'PROVIDER:externalId' 형식이어야 합니다");
        }
        String provider = raw.substring(0, separator);
        try {
            return new ContentKey(ProviderType.valueOf(provider), raw.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "알 수 없는 콘텐츠 제공자입니다: " + provider);
        }
    }
}
