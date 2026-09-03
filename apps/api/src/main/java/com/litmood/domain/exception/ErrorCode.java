package com.litmood.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 카탈로그 (docs/05-api-spec.md).
 * title 은 그대로 사용자에게 노출되므로 한국어로 작성한다.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    RATING_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "아직 보지 않은 콘텐츠에는 별점을 남길 수 없습니다"),
    MOOD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "무드는 최대 5개까지 선택할 수 있습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "보안을 위해 모든 기기에서 로그아웃되었습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다"),
    RECORD_DUPLICATE(HttpStatus.CONFLICT, "이미 기록한 콘텐츠입니다"),
    HANDLE_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "이미 가입된 이메일입니다"),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "콘텐츠 검색 서비스에 일시적인 문제가 있습니다"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem Details 의 {@code type} URI. */
    public String typeUri() {
        return "https://litmood.app/errors/" + name().toLowerCase().replace('_', '-');
    }
}
