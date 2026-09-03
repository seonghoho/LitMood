package com.litmood;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.litmood.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * F-02 — 통합 검색.
 *
 * 실제 외부 API 를 호출하면 테스트가 네트워크와 rate limit 에 의존하게 된다.
 * WireMock 으로 provider 를 대역화해 <b>정상·장애·지연</b>을 결정적으로 재현한다.
 */
class ContentSearchTest extends IntegrationTest {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    StringRedisTemplate redis;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK.port();
        registry.add("litmood.provider.naver.base-url", () -> base);
        registry.add("litmood.provider.naver.client-id", () -> "test-id");
        registry.add("litmood.provider.naver.client-secret", () -> "test-secret");
        registry.add("litmood.provider.tmdb.base-url", () -> base);
        registry.add("litmood.provider.tmdb.api-key", () -> "test-key");
        registry.add("litmood.provider.spotify.base-url", () -> base);
        registry.add("litmood.provider.spotify.token-url", () -> base + "/api/token");
        registry.add("litmood.provider.spotify.client-id", () -> "test-id");
        registry.add("litmood.provider.spotify.client-secret", () -> "test-secret");
        registry.add("litmood.provider.timeout-ms", () -> 1500);
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
        // 캐시가 남아 있으면 provider 대역화 결과가 가려진다
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("세 provider 의 결과를 타입별로 정규화해 반환한다")
    void searchNormalizesAllProviders() {
        stubNaver();
        stubTmdb();
        stubSpotify();

        Map<String, Object> body = search("노르웨이의 숲");

        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> results = (Map<String, List<Map<String, Object>>>) body.get("results");

        assertThat(results.get("BOOK")).hasSize(1);
        assertThat(results.get("BOOK").get(0))
                .containsEntry("title", "노르웨이의 숲") // <b> 태그가 제거되어야 한다
                .containsEntry("provider", "NAVER_BOOK")
                .containsEntry("creators", List.of("무라카미 하루키", "양억관"));

        assertThat(results.get("MOVIE")).hasSize(1);
        assertThat(results.get("MOVIE").get(0)).containsEntry("provider", "TMDB");

        assertThat(results.get("MUSIC")).hasSize(1);
        assertThat(results.get("MUSIC").get(0)).containsEntry("provider", "SPOTIFY");

        assertThat(body.get("failedProviders")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
    }

    @Test
    @DisplayName("한 provider 가 장애여도 나머지 결과는 정상 반환하고 실패를 알린다 (NFR-03)")
    void partialFailureIsTolerated() {
        stubNaver();
        stubSpotify();
        // TMDB 만 500 을 반환한다
        WIREMOCK.stubFor(get(urlPathEqualTo("/search/movie")).willReturn(aResponse().withStatus(500)));

        Map<String, Object> body = search("노르웨이의 숲");

        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> results = (Map<String, List<Map<String, Object>>>) body.get("results");

        assertThat(results.get("BOOK")).hasSize(1);
        assertThat(results.get("MUSIC")).hasSize(1);
        assertThat(results.get("MOVIE")).isEmpty();
        assertThat(body.get("failedProviders")).isEqualTo(List.of("TMDB"));
    }

    @Test
    @DisplayName("느린 provider 는 타임아웃 처리되고 전체 응답을 막지 않는다 (NFR-02)")
    void slowProviderTimesOut() {
        stubNaver();
        stubSpotify();
        // 타임아웃(1.5s)을 넘기는 지연
        WIREMOCK.stubFor(get(urlPathEqualTo("/search/movie"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(4000).withBody("{\"results\":[]}")));

        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = search("노르웨이의 숲");
        long elapsed = System.currentTimeMillis() - startedAt;

        @SuppressWarnings("unchecked")
        Map<String, List<Map<String, Object>>> results = (Map<String, List<Map<String, Object>>>) body.get("results");

        assertThat(results.get("BOOK")).hasSize(1); // 빠른 provider 는 영향받지 않는다
        assertThat(body.get("failedProviders")).isEqualTo(List.of("TMDB"));
        assertThat(elapsed).as("느린 provider 에 전체가 끌려가면 안 된다").isLessThan(4000);
    }

    @Test
    @DisplayName("동일 검색어 재요청은 캐시에서 응답하며 provider 를 다시 호출하지 않는다 (F-02-04)")
    void secondSearchHitsCache() {
        stubNaver();
        stubTmdb();
        stubSpotify();

        search("노르웨이의 숲");
        Map<String, Object> second = search("노르웨이의 숲");

        assertThat(second.get("cached")).isEqualTo(true);
        // 각 provider 는 첫 요청에서만 호출된다
        WIREMOCK.verify(1, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathEqualTo("/v1/search/book.json")));
    }

    @Test
    @DisplayName("검색어가 비면 400 과 VALIDATION_FAILED 를 응답한다")
    void blankQueryRejected() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/contents/search?q=", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> search(String query) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/contents/search?q={q}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {},
                query);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void stubNaver() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/v1/search/book.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {"items":[{
                                  "title":"<b>노르웨이의</b> 숲",
                                  "author":"무라카미 하루키^양억관",
                                  "isbn":"9788937473135",
                                  "publisher":"민음사",
                                  "pubdate":"20170825",
                                  "image":"https://shopping-phinf.pstatic.net/cover.jpg",
                                  "description":"<b>스무</b> 살의 이야기"
                                }]}
                                """)));
    }

    private void stubTmdb() {
        WIREMOCK.stubFor(get(urlPathEqualTo("/search/movie"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {"results":[{
                                  "id":296,
                                  "title":"상실의 시대",
                                  "original_title":"ノルウェイの森",
                                  "release_date":"2010-12-11",
                                  "poster_path":"/poster.jpg",
                                  "overview":"줄거리",
                                  "vote_average":6.4
                                }]}
                                """)));
    }

    private void stubSpotify() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/api/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-token\",\"expires_in\":3600}")));

        WIREMOCK.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {"tracks":{"items":[{
                                  "id":"4uLU6hMCjMI75M1A2tKUQC",
                                  "name":"Norwegian Wood",
                                  "duration_ms":125000,
                                  "artists":[{"name":"The Beatles"}],
                                  "album":{
                                    "name":"Rubber Soul",
                                    "release_date":"1965-12-03",
                                    "release_date_precision":"day",
                                    "images":[{"url":"https://i.scdn.co/image/cover.jpg"}]
                                  },
                                  "external_ids":{"isrc":"GBAYE0601696"},
                                  "external_urls":{"spotify":"https://open.spotify.com/track/x"}
                                }]}}
                                """)));
    }
}
