package com.litmood.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code litmood.*} 설정을 타입 안전하게 바인딩한다.
 * 시크릿은 코드가 아닌 환경변수에서만 주입된다 (NFR-04).
 */
@ConfigurationProperties(prefix = "litmood")
public record LitmoodProperties(
        Jwt jwt, Cache cache, Provider provider, Storage storage, Cors cors, Admin admin) {

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlSeconds) {}

    public record Cache(int searchTtlHours) {}

    public record Provider(int timeoutMs, Naver naver, Tmdb tmdb, Spotify spotify) {
        public record Naver(String clientId, String clientSecret, String baseUrl) {}

        public record Tmdb(String apiKey, String baseUrl) {}

        public record Spotify(String clientId, String clientSecret, String baseUrl, String tokenUrl) {}
    }

    public record Storage(String endpoint, String bucket, String accessKey, String secretKey) {}

    public record Cors(List<String> allowedOrigins) {}

    /**
     * 운영자 계정 (#28).
     *
     * <p>권한을 DB 가 아니라 배포 설정에 두어, 코드도 데이터도 고치지 않고 바뀐다.
     * 운영자가 한둘인 동안의 선택이며, 목록이 길어지면 감사 흔적이 남는 테이블로 옮긴다.
     */
    public record Admin(List<String> handles) {}
}
