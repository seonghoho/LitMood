package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.User;
import com.litmood.domain.model.Visibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 프로필 관련 요청/응답 (docs/05-api-spec.md). */
public final class UserDtos {

    private UserDtos() {}

    /** PATCH 이므로 모든 필드가 선택이다 — 넣지 않은 필드는 변경되지 않는다. */
    @Schema(name = "UpdateProfileRequest")
    public record UpdateProfileRequest(
            @Size(min = 1, max = 50, message = "닉네임은 1~50자여야 합니다") String nickname,
            @Size(max = 200, message = "소개는 200자를 넘을 수 없습니다") String bio,
            Visibility defaultVisibility,
            String avatarUrl) {}

    /** 내 정보 — 공개 프로필과 달리 email 과 기본 공개범위를 포함한다. */
    @Schema(name = "MyProfile")
    public record MyProfile(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String email,
            @Schema(requiredMode = REQUIRED) String handle,
            @Schema(requiredMode = REQUIRED) String nickname,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String bio,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String avatarUrl,
            @Schema(requiredMode = REQUIRED) Visibility defaultVisibility) {

        public static MyProfile from(User user) {
            return new MyProfile(
                    user.getId(),
                    user.getEmail(),
                    user.getHandle(),
                    user.getNickname(),
                    user.getBio(),
                    user.getAvatarUrl(),
                    user.getDefaultVisibility());
        }
    }

    @Schema(name = "AvatarUploadRequest")
    public record AvatarUploadRequest(
            @NotBlank(message = "이미지 형식을 알 수 없습니다") String contentType,
            @NotNull(message = "파일 크기를 알 수 없습니다") @Positive Long contentLength) {}

    @Schema(name = "AvatarUploadResponse", description = "uploadUrl 로 PUT 한 뒤 publicUrl 을 PATCH /users/me 에 저장한다")
    public record AvatarUploadResponse(
            @Schema(requiredMode = REQUIRED) String uploadUrl,
            @Schema(requiredMode = REQUIRED) String publicUrl) {}
}
