package com.litmood.infrastructure.config;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이 설정에서 생성되는 OpenAPI 문서가 API 계약의 단일 진실 원천이다 (ADR-008).
 * 여기서 파생되어 packages/api-client 타입과 Postman 컬렉션이 만들어진다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI litmoodOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LitMood API")
                        .version("v1")
                        .description("책·영화·음악을 무드와 함께 기록하고 나누는 서비스의 API"))
                .servers(List.of(new Server().url("http://localhost:8080").description("local")))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /**
     * enum 을 프로퍼티마다 인라인하지 않고 공유 스키마로 참조하게 한다.
     *
     * <p>기본 동작은 enum 을 쓰는 자리마다 값 목록을 복제해 넣는다. 그러면 코드젠이
     * {@code ContentRefType}, {@code ContentSummaryType} 처럼 같은 enum 을 별개 타입으로
     * 19개나 만들어 내고, 프론트에서 {@code Record<ContentType, string>} 같은
     * "라벨은 콘텐츠 타입마다 하나" 라는 관계를 타입으로 표현할 수 없게 된다.
     * $ref 로 내보내면 도메인 enum 하나가 TS 타입 하나로 대응된다 (ADR-008).
     *
     * <p>플래그만 켜고 {@link ModelResolver} 빈은 직접 만들지 않는다. 빈을 대체하면
     * springdoc 이 자기 ObjectMapper 로 구성해 둔 리졸버가 밀려나면서 스키마에서
     * {@code type: object} 가 통째로 빠진다(31개 → 4개). 플래그는 스키마를 해석하는
     * 시점에 읽히므로 여기서 세팅해도 늦지 않다.
     */
    @PostConstruct
    void enableSharedEnumSchemas() {
        ModelResolver.enumsAsRef = true;
    }
}
