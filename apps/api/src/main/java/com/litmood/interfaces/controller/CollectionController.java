package com.litmood.interfaces.controller;

import com.litmood.application.service.CollectionService;
import com.litmood.infrastructure.security.AuthPrincipal;
import com.litmood.infrastructure.security.CurrentUser;
import com.litmood.interfaces.dto.CollectionDtos.AddCollectionItemRequest;
import com.litmood.interfaces.dto.CollectionDtos.CollectionResponse;
import com.litmood.interfaces.dto.CollectionDtos.CreateCollectionRequest;
import com.litmood.interfaces.dto.CollectionDtos.ReorderItemsRequest;
import com.litmood.interfaces.dto.CollectionDtos.UpdateCollectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
@Tag(name = "Collections", description = "컬렉션")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    @Operation(summary = "컬렉션 생성", description = "제목에서 공유용 slug 를 만든다")
    public ResponseEntity<CollectionResponse> create(
            @CurrentUser AuthPrincipal principal, @Valid @RequestBody CreateCollectionRequest request) {
        CollectionResponse created = collectionService.create(principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/collections/" + created.slug())).body(created);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "컬렉션 조회", description = "공개 범위를 벗어나면 404 로 응답한다")
    public CollectionResponse get(@CurrentUser AuthPrincipal principal, @PathVariable String slug) {
        return collectionService.get(principal == null ? null : principal.userId(), slug);
    }

    @PatchMapping("/{slug}")
    @Operation(summary = "컬렉션 수정")
    public CollectionResponse update(
            @CurrentUser AuthPrincipal principal,
            @PathVariable String slug,
            @Valid @RequestBody UpdateCollectionRequest request) {
        return collectionService.update(principal.userId(), slug, request);
    }

    @DeleteMapping("/{slug}")
    @Operation(summary = "컬렉션 삭제")
    public ResponseEntity<Void> delete(@CurrentUser AuthPrincipal principal, @PathVariable String slug) {
        collectionService.delete(principal.userId(), slug);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{slug}/items")
    @Operation(summary = "콘텐츠 추가", description = "기록하지 않은 콘텐츠도 담을 수 있다")
    public CollectionResponse addItem(
            @CurrentUser AuthPrincipal principal,
            @PathVariable String slug,
            @Valid @RequestBody AddCollectionItemRequest request) {
        return collectionService.addItem(principal.userId(), slug, request);
    }

    @DeleteMapping("/{slug}/items/{contentId}")
    @Operation(summary = "콘텐츠 제거")
    public CollectionResponse removeItem(
            @CurrentUser AuthPrincipal principal, @PathVariable String slug, @PathVariable Long contentId) {
        return collectionService.removeItem(principal.userId(), slug, contentId);
    }

    @PatchMapping("/{slug}/items/order")
    @Operation(summary = "순서 일괄 변경", description = "큐레이션에서 순서는 의미다")
    public CollectionResponse reorder(
            @CurrentUser AuthPrincipal principal,
            @PathVariable String slug,
            @Valid @RequestBody ReorderItemsRequest request) {
        return collectionService.reorder(principal.userId(), slug, request);
    }
}
