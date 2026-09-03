package com.litmood.interfaces.controller;

import com.litmood.application.port.AvatarStorage;
import com.litmood.application.service.CollectionService;
import com.litmood.application.service.RecordService;
import com.litmood.application.service.SocialService;
import com.litmood.application.service.UserService;
import com.litmood.application.service.RecordService.TimelineFilter;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.UserRepository;
import com.litmood.infrastructure.security.AuthPrincipal;
import com.litmood.infrastructure.security.CurrentUser;
import com.litmood.interfaces.dto.CollectionDtos.CollectionSummary;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
import com.litmood.interfaces.dto.UserDtos.AvatarUploadRequest;
import com.litmood.interfaces.dto.UserDtos.AvatarUploadResponse;
import com.litmood.interfaces.dto.UserDtos.MyProfile;
import com.litmood.interfaces.dto.UserDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "사용자")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final RecordService recordService;
    private final CollectionService collectionService;
    private final SocialService socialService;

    public UserController(
            UserRepository userRepository,
            UserService userService,
            RecordService recordService,
            CollectionService collectionService,
            SocialService socialService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.recordService = recordService;
        this.collectionService = collectionService;
        this.socialService = socialService;
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    public MyProfile me(@CurrentUser AuthPrincipal principal) {
        return MyProfile.from(userService.me(principal.userId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정", description = "넣지 않은 필드는 변경되지 않는다. 핸들은 바꿀 수 없다")
    public MyProfile updateProfile(
            @CurrentUser AuthPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return MyProfile.from(userService.updateProfile(
                principal.userId(),
                request.nickname(),
                request.bio(),
                request.defaultVisibility(),
                request.avatarUrl()));
    }

    @PostMapping("/me/avatar")
    @Operation(
            summary = "아바타 업로드 URL 발급",
            description = "발급받은 uploadUrl 로 직접 PUT 한 뒤, publicUrl 을 PATCH /users/me 로 저장한다")
    public AvatarUploadResponse issueAvatarUpload(
            @CurrentUser AuthPrincipal principal, @Valid @RequestBody AvatarUploadRequest request) {
        AvatarStorage.PresignedUpload upload =
                userService.issueAvatarUpload(principal.userId(), request.contentType(), request.contentLength());
        return new AvatarUploadResponse(upload.uploadUrl(), upload.publicUrl());
    }

    /**
     * 공개 프로필 (NFR-06).
     * 검색 유입 경로이므로 비로그인 상태에서도 접근 가능해야 한다.
     */
    @GetMapping("/@{handle}")
    @SecurityRequirements
    @Operation(summary = "공개 프로필 조회")
    public PublicProfile profile(@CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        User user = userRepository
                .findActiveByHandle(handle)
                .orElseThrow(() -> LitmoodException.notFound("사용자"));

        Long viewerId = principal == null ? null : principal.userId();
        SocialService.FollowStats stats = socialService.stats(viewerId, user);
        return PublicProfile.from(user, stats);
    }

    @GetMapping("/@{handle}/records")
    @SecurityRequirements
    @Operation(summary = "공개 기록 목록", description = "PUBLIC 기록만 노출한다")
    public RecordPage publicRecords(
            @CurrentUser AuthPrincipal principal,
            @PathVariable String handle,
            @RequestParam(required = false) List<ContentType> types,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return recordService.publicTimeline(
                handle,
                principal == null ? null : principal.userId(),
                new TimelineFilter(types, null, null, null, null, null),
                cursor,
                limit);
    }

    @GetMapping("/@{handle}/collections")
    @SecurityRequirements
    @Operation(summary = "공개 컬렉션 목록", description = "본인이 조회하면 비공개 컬렉션도 포함된다")
    public List<CollectionSummary> collections(
            @CurrentUser AuthPrincipal principal, @PathVariable String handle) {
        return collectionService.listByHandle(handle, principal == null ? null : principal.userId());
    }

    @Schema(name = "PublicProfile")
    public record PublicProfile(
            String handle,
            String nickname,
            String bio,
            String avatarUrl,
            long followers,
            long following,
            boolean followedByMe) {

        static PublicProfile from(User user, SocialService.FollowStats stats) {
            return new PublicProfile(
                    user.getHandle(),
                    user.getNickname(),
                    user.getBio(),
                    user.getAvatarUrl(),
                    stats.followers(),
                    stats.following(),
                    stats.followedByMe());
        }
    }
}
