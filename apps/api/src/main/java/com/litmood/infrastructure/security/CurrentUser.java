package com.litmood.infrastructure.security;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러에서 인증 주체를 받는다.
 * {@code @Parameter(hidden)} 로 OpenAPI 스펙에서 제외해, 생성되는
 * 클라이언트 훅에 내부 타입이 새지 않게 한다 (ADR-008).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
@Parameter(hidden = true, in = ParameterIn.HEADER)
public @interface CurrentUser {}
