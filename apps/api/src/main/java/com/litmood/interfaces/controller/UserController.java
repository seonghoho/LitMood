package com.litmood.interfaces.controller;

import com.litmood.application.service.CollectionService;
import com.litmood.application.service.RecordService;
import com.litmood.application.service.RecordService.TimelineFilter;
import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.UserRepository;
import com.litmood.infrastructure.security.AuthPrincipal;
import com.litmood.infrastructure.security.CurrentUser;
import com.litmood.interfaces.dto.AuthDtos.UserSummary;
import com.litmood.interfaces.dto.CollectionDtos.CollectionSummary;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
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
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "사용자")
public class UserController {

    private final UserRepository userRepository;
    private final RecordService recordService;
    private final CollectionService collectionService;

    public UserController(
            UserRepository userRepository,
            RecordService recordService,
            CollectionService collectionService) {
        this.userRepository = userRepository;
        this.recordService = recordService;
        this.collectionService = collectionService;
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public UserSummary me(@CurrentUser AuthPrincipal principal) {
        return userRepository
                .findById(principal.userId())
                .map(UserSummary::from)
                .orElseThrow(() -> new LitmoodException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 공개 프로필 (NFR-06).
     * 검색 유입 경로이므로 비로그인 상태에서도 접근 가능해야 한다.
     */
    @GetMapping("/@{handle}")
    @SecurityRequirements
    @Operation(summary = "공개 프로필 조회")
    public PublicProfile profile(@PathVariable String handle) {
        User user = userRepository
                .findActiveByHandle(handle)
                .orElseThrow(() -> LitmoodException.notFound("사용자"));
        return PublicProfile.from(user);
    }

    @GetMapping("/@{handle}/records")
    @SecurityRequirements
    @Operation(summary = "공개 기록 목록", description = "PUBLIC 기록만 노출한다")
    public RecordPage publicRecords(
            @PathVariable String handle,
            @RequestParam(required = false) List<ContentType> types,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return recordService.publicTimeline(
                handle, new TimelineFilter(types, null, null, null, null, null), cursor, limit);
    }

    @GetMapping("/@{handle}/collections")
    @SecurityRequirements
    @Operation(summary = "공개 컬렉션 목록", description = "본인이 조회하면 비공개 컬렉션도 포함된다")
    public List<CollectionSummary> collections(
            @CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        return collectionService.listByHandle(handle, principal == null ? null : principal.userId());
    }

    @Schema(name = "PublicProfile")
    public record PublicProfile(String handle, String nickname, String bio, String avatarUrl) {
        static PublicProfile from(User user) {
            return new PublicProfile(
                    user.getHandle(), user.getNickname(), user.getBio(), user.getAvatarUrl());
        }
    }
}
