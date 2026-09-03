package com.litmood.domain.exception;

/**
 * 도메인 전반의 기본 예외.
 * domain 레이어는 HTTP 를 모르지만, ErrorCode 가 상태 매핑을 들고 있어
 * interfaces 레이어에서 변환 없이 응답할 수 있다.
 */
public class LitmoodException extends RuntimeException {

    private final ErrorCode errorCode;

    public LitmoodException(ErrorCode errorCode) {
        super(errorCode.title());
        this.errorCode = errorCode;
    }

    public LitmoodException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static LitmoodException notFound(String what) {
        return new LitmoodException(ErrorCode.NOT_FOUND, what + "을(를) 찾을 수 없습니다");
    }
}
