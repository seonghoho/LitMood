package com.litmood.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 스켈레톤 검증용 엔드포인트. M1 에서 실제 도메인 컨트롤러로 대체된다. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "시스템 상태")
public class HealthController {

    @GetMapping("/ping")
    @SecurityRequirements // 공개 엔드포인트 — OpenAPI 에서 인증 요구사항 제외
    @Operation(summary = "헬스 체크", description = "애플리케이션 기동 여부를 확인한다")
    public Map<String, Object> ping() {
        return Map.of("service", "litmood-api", "status", "ok", "time", Instant.now().toString());
    }
}
