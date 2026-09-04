package com.litmood.interfaces.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.litmood.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 인증 관련 요청/응답 (docs/05-api-spec.md). */
public final class AuthDtos {

    private AuthDtos() {}

    @Schema(name = "SignupRequest")
    public record SignupRequest(
            @Email(message = "올바른 이메일 형식이 아닙니다") @NotBlank(message = "이메일을 입력해 주세요") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요")
                    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
                    String password,
            @NotBlank(message = "아이디를 입력해 주세요")
                    @Pattern(
                            regexp = "^[a-zA-Z0-9_]{3,30}$",
                            message = "아이디는 영문·숫자·밑줄 3~30자여야 합니다")
                    String handle,
            @NotBlank(message = "닉네임을 입력해 주세요") @Size(max = 50) String nickname) {}

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @Email @NotBlank(message = "이메일을 입력해 주세요") String email,
            @NotBlank(message = "비밀번호를 입력해 주세요") String password) {}

    @Schema(name = "AuthResponse")
    public record AuthResponse(
            @Schema(requiredMode = REQUIRED) String accessToken,
            @Schema(requiredMode = REQUIRED) long expiresIn,
            @Schema(requiredMode = REQUIRED) UserSummary user) {}

    @Schema(name = "UserSummary")
    public record UserSummary(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String handle,
            @Schema(requiredMode = REQUIRED) String nickname,
            @Schema(requiredMode = REQUIRED, types = {"string", "null"}) String avatarUrl) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getHandle(), user.getNickname(), user.getAvatarUrl());
        }
    }
}
