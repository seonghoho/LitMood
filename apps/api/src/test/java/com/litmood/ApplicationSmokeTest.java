package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;

import com.litmood.support.IntegrationTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** M0 배선 검증 — 컨텍스트 기동, Flyway 마이그레이션, 공개 엔드포인트. */
class ApplicationSmokeTest extends IntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 기동하고 ping 이 응답한다")
    void ping() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("litmood-api");
    }

    @Test
    @DisplayName("인증이 필요한 엔드포인트는 비로그인 요청을 거부한다")
    void protectedEndpointRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/records/me", String.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Flyway 마이그레이션으로 스키마와 큐레이션 무드 시드가 적용된다")
    void migrationsApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM moods WHERE is_curated")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(12);
            }

            // 도메인 불변식 1 — 한 사용자 · 한 콘텐츠 · 한 기록 (부분 유니크 인덱스)
            try (ResultSet rs = statement.executeQuery(
                    "SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_records_user_content'")) {
                rs.next();
                assertThat(rs.getString(1)).contains("deleted_at IS NULL");
            }
        }
    }
}
