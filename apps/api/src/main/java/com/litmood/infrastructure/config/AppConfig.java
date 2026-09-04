package com.litmood.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableConfigurationProperties(LitmoodProperties.class)
public class AppConfig {

    /**
     * 외부 provider 호출용 RestClient 의 공통 타임아웃 (NFR-02).
     * 개별 provider 가 느려도 검색 전체가 끌려가지 않도록 연결/읽기 타임아웃을 명시한다.
     * 가상 스레드 환경이므로 스레드 블로킹 자체는 비용이 낮다 (ADR-003).
     */
    @Bean
    RestClientCustomizer providerRestClientCustomizer(LitmoodProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(properties.provider().timeoutMs()));
        return builder -> builder.requestFactory(factory);
    }
}
