package com.litmood.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
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
}
