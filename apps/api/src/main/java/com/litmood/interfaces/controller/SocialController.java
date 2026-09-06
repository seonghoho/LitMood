package com.litmood.interfaces.controller;

import com.litmood.application.service.SocialService;
import com.litmood.infrastructure.security.AuthPrincipal;
import com.litmood.infrastructure.security.CurrentUser;
import com.litmood.interfaces.dto.SocialDtos.BlockedUserResponse;
import com.litmood.interfaces.dto.SocialDtos.LikeResponse;
import com.litmood.interfaces.dto.SocialDtos.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F-06 — 팔로우 / 좋아요 / 차단 / 신고. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Social", description = "팔로우·좋아요·차단·신고")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    // ── 팔로우 ──────────────────────────────────────────────

    @PostMapping("/users/@{handle}/follow")
    @Operation(summary = "팔로우", description = "비대칭 관계 — 승인이 필요 없다. 이미 팔로우 중이면 멱등하게 성공한다.")
    public ResponseEntity<Void> follow(@CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        socialService.follow(principal.userId(), handle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/@{handle}/follow")
    @Operation(summary = "언팔로우")
    public ResponseEntity<Void> unfollow(@CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        socialService.unfollow(principal.userId(), handle);
        return ResponseEntity.noContent().build();
    }

    // ── 차단 ────────────────────────────────────────────────

    @PostMapping("/users/@{handle}/block")
    @Operation(summary = "차단", description = "양방향으로 가려지며 기존 팔로우 관계는 양쪽 모두 해제된다")
    public ResponseEntity<Void> block(@CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        socialService.block(principal.userId(), handle);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/blocks")
    @Operation(summary = "내가 차단한 사용자", description = "최근 차단한 순. 여기서만 차단을 되돌릴 수 있다.")
    public List<BlockedUserResponse> blocks(@CurrentUser AuthPrincipal principal) {
        return socialService.listBlocked(principal.userId());
    }

    @DeleteMapping("/users/@{handle}/block")
    @Operation(summary = "차단 해제")
    public ResponseEntity<Void> unblock(@CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        socialService.unblock(principal.userId(), handle);
        return ResponseEntity.noContent().build();
    }

    // ── 좋아요 ──────────────────────────────────────────────

    @PostMapping("/records/{id}/like")
    @Operation(summary = "기록 좋아요")
    public LikeResponse likeRecord(@CurrentUser AuthPrincipal principal, @PathVariable Long id) {
        return new LikeResponse(socialService.likeRecord(principal.userId(), id), true);
    }

    @DeleteMapping("/records/{id}/like")
    @Operation(summary = "기록 좋아요 취소")
    public LikeResponse unlikeRecord(@CurrentUser AuthPrincipal principal, @PathVariable Long id) {
        return new LikeResponse(socialService.unlikeRecord(principal.userId(), id), false);
    }

    @PostMapping("/collections/{slug}/like")
    @Operation(summary = "컬렉션 좋아요")
    public LikeResponse likeCollection(@CurrentUser AuthPrincipal principal, @PathVariable String slug) {
        return new LikeResponse(socialService.likeCollection(principal.userId(), slug), true);
    }

    @DeleteMapping("/collections/{slug}/like")
    @Operation(summary = "컬렉션 좋아요 취소")
    public LikeResponse unlikeCollection(@CurrentUser AuthPrincipal principal, @PathVariable String slug) {
        return new LikeResponse(socialService.unlikeCollection(principal.userId(), slug), false);
    }

    // ── 신고 ────────────────────────────────────────────────

    @PostMapping("/reports")
    @Operation(summary = "신고", description = "같은 대상을 반복 신고해도 한 번만 접수된다")
    public ResponseEntity<Void> report(
            @CurrentUser AuthPrincipal principal, @Valid @RequestBody ReportRequest request) {
        socialService.report(
                principal.userId(),
                request.targetType(),
                request.targetId(),
                request.reason(),
                request.detail());
        return ResponseEntity.accepted().build();
    }
}
