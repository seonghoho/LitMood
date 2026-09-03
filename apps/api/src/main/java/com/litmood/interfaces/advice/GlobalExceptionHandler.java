package com.litmood.interfaces.advice;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 에러 응답을 RFC 9457 Problem Details 로 통일한다 (docs/03-architecture.md).
 * 프론트는 {@code code} 로 분기하고 {@code title} 을 그대로 사용자에게 노출한다.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} 가 필요한 이유: Spring 의 내장
 * {@code ProblemDetailsExceptionHandler} 가 {@code @Order(0)} 으로 등록되어 있어,
 * 우선순위를 올리지 않으면 검증 실패 같은 MVC 예외를 내장 핸들러가 먼저 가로챈다.
 * 그러면 응답에 {@code code} 가 없어 프론트가 분기할 수 없다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LitmoodException.class)
    ProblemDetail handleLitmood(LitmoodException ex, HttpServletRequest request) {
        return problem(ex.errorCode(), ex.getMessage(), request);
    }

    /** {@code @Valid @RequestBody} 검증 실패 — 필드별 사유를 함께 싣는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "reason", String.valueOf(fe.getDefaultMessage())))
                .toList();

        ProblemDetail detail = problem(ErrorCode.VALIDATION_FAILED, "입력값을 확인해 주세요", request);
        detail.setProperty("errors", errors);
        return detail;
    }

    /** {@code @Validated} 가 붙은 쿼리 파라미터 검증 실패. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleParamValidation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", lastPathSegment(v.getPropertyPath().toString()), "reason", v.getMessage()))
                .toList();

        ProblemDetail detail = problem(ErrorCode.VALIDATION_FAILED, "요청 값을 확인해 주세요", request);
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return problem(ErrorCode.VALIDATION_FAILED, "필수 파라미터가 없습니다: " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problem(ErrorCode.VALIDATION_FAILED, "'%s' 값의 형식이 올바르지 않습니다".formatted(ex.getName()), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // 파싱 실패 원문에는 내부 타입 정보가 섞여 있어 그대로 노출하지 않는다
        return problem(ErrorCode.VALIDATION_FAILED, "요청 본문을 해석할 수 없습니다", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // 내부 메시지는 응답에 싣지 않는다. 원인은 로그에만 남긴다.
        log.error("처리되지 않은 예외: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(ErrorCode.INTERNAL_ERROR, null, request);
    }

    private ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(URI.create(code.typeUri()));
        problem.setTitle(code.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        if (detail != null && !detail.equals(code.title())) {
            problem.setDetail(detail);
        }
        return problem;
    }

    private String lastPathSegment(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index < 0 ? propertyPath : propertyPath.substring(index + 1);
    }
}
