package com.litmood;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
import com.litmood.interfaces.dto.RecordDtos.RecordResponse;
import com.litmood.interfaces.dto.RecordDtos.UpdateRecordRequest;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Visibility;
import com.litmood.support.AuthenticatedTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** F-03·F-04 — 기록 생성/수정/삭제, 스냅샷, 타임라인, 공개 범위. */
class RecordApiTest extends AuthenticatedTest {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK.port();
        registry.add("litmood.provider.naver.base-url", () -> base);
        registry.add("litmood.provider.naver.client-id", () -> "test-id");
        registry.add("litmood.provider.naver.client-secret", () -> "test-secret");
    }

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    @BeforeEach
    void stubBookLookup() {
        WIREMOCK.resetAll();
        // 상세 조회(book_adv)로 스냅샷을 만든다
        WIREMOCK.stubFor(get(urlPathMatching("/v1/search/book.*"))
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
                                  "image":"https://shopping-phinf.pstatic.net/x.jpg",
                                  "description":"스무 살의 이야기"
                                }]}
                                """)));
    }

    @Test
    @DisplayName("기록을 만들면 외부 콘텐츠가 자체 DB 에 스냅샷으로 저장된다 (F-02-05)")
    void createRecordSnapshotsContent() {
        AuthResponse me = signupNewUser();

        ResponseEntity<RecordResponse> response = createRecord(
                me,
                new CreateRecordRequest(
                        ProviderType.NAVER_BOOK, "9788937473135", RecordStatus.DONE,
                        new BigDecimal("4.5"), List.of("새벽", "먹먹함"),
                        "스무 살에 읽었을 때와 다르다.", false, Visibility.PUBLIC, null, null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RecordResponse record = response.getBody();
        assertThat(record).isNotNull();
        assertThat(record.content().title()).isEqualTo("노르웨이의 숲"); // 태그 제거된 상태로 저장
        assertThat(record.content().creators()).containsExactly("무라카미 하루키", "양억관");
        assertThat(record.content().id()).isNotNull(); // 자체 DB id 가 부여됐다
        assertThat(record.moods()).extracting("name").containsExactly("새벽", "먹먹함");
        // 시드된 큐레이션 무드와 연결되어 색을 갖는다
        assertThat(record.moods().get(0).color()).isEqualTo("#2A3B6B");
        assertThat(record.moods().get(0).curated()).isTrue();
    }

    @Test
    @DisplayName("두 번째 기록은 provider 를 다시 호출하지 않고 저장된 스냅샷을 재사용한다")
    void secondRecordReusesSnapshot() {
        createRecord(signupNewUser(), simpleRequest(RecordStatus.DONE));
        WIREMOCK.resetRequests();

        createRecord(signupNewUser(), simpleRequest(RecordStatus.WANT));

        WIREMOCK.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                urlPathMatching("/v1/search/book.*")));
    }

    @Test
    @DisplayName("불변식 1 — 같은 콘텐츠를 두 번 기록하면 409 로 거부된다")
    void duplicateRecordRejected() {
        AuthResponse me = signupNewUser();
        createRecord(me, simpleRequest(RecordStatus.DONE));

        ResponseEntity<Map<String, Object>> second = authedMap(
                me, HttpMethod.POST, "/api/v1/records", simpleRequest(RecordStatus.DOING));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("code", "RECORD_DUPLICATE");
    }

    @Test
    @DisplayName("WANT 상태에 별점을 주면 400 RATING_NOT_ALLOWED 로 거부된다")
    void wantWithRatingRejected() {
        AuthResponse me = signupNewUser();

        ResponseEntity<Map<String, Object>> response = authedMap(
                me, HttpMethod.POST, "/api/v1/records",
                new CreateRecordRequest(
                        ProviderType.NAVER_BOOK, "9788937473135", RecordStatus.WANT,
                        new BigDecimal("4.0"), null, null, null, null, null, null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "RATING_NOT_ALLOWED");
    }

    @Test
    @DisplayName("상태만으로도 기록할 수 있다 — 필수 입력은 status 하나뿐이다 (F-03-01)")
    void statusOnlyRecordIsValid() {
        AuthResponse me = signupNewUser();

        ResponseEntity<RecordResponse> response = createRecord(me, simpleRequest(RecordStatus.WANT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().rating()).isNull();
        assertThat(response.getBody().moods()).isEmpty();
        // 공개 범위를 지정하지 않으면 사용자 기본값을 따른다
        assertThat(response.getBody().visibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    @DisplayName("null 필드도 응답에 키로 존재한다 — OpenAPI 계약과 일치해야 한다")
    void nullFieldsArePresentInResponse() {
        AuthResponse me = signupNewUser();
        createRecord(me, simpleRequest(RecordStatus.WANT));

        // 스펙은 rating 을 "존재하며 nullable" 로 선언한다. 키가 생략되면
        // 스펙에서 생성된 클라이언트의 널 검사(rating !== null)가 통과해 버린다.
        ResponseEntity<Map<String, Object>> response =
                authedMap(me, HttpMethod.GET, "/api/v1/records/me", null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item).containsKey("rating").containsKey("review").containsKey("contextNote");
            assertThat(item.get("rating")).isNull();
        });
    }

    @Test
    @DisplayName("본인만 기록을 수정·삭제할 수 있다")
    void onlyOwnerCanModify() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        Long recordId = createRecord(owner, simpleRequest(RecordStatus.DONE)).getBody().id();

        ResponseEntity<Map<String, Object>> patch = authedMap(
                stranger, HttpMethod.PATCH, "/api/v1/records/" + recordId,
                new UpdateRecordRequest(null, null, null, "남의 기록 수정", null, null, null, null, null, null));
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> delete =
                authedMap(stranger, HttpMethod.DELETE, "/api/v1/records/" + recordId, null);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("삭제한 기록은 타임라인에서 사라지고, 같은 콘텐츠를 다시 기록할 수 있다")
    void softDeleteAllowsRerecording() {
        AuthResponse me = signupNewUser();
        Long recordId = createRecord(me, simpleRequest(RecordStatus.DONE)).getBody().id();

        assertThat(authedMap(me, HttpMethod.DELETE, "/api/v1/records/" + recordId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(timeline(me).items()).isEmpty();

        // 부분 유니크 인덱스(deleted_at IS NULL) 덕분에 재기록이 가능하다
        assertThat(createRecord(me, simpleRequest(RecordStatus.DOING)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("PRIVATE 기록은 타인에게 404 로 응답해 존재 자체를 숨긴다")
    void privateRecordHiddenFromOthers() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        Long recordId = createRecord(
                        owner,
                        new CreateRecordRequest(
                                ProviderType.NAVER_BOOK, "9788937473135", RecordStatus.DONE, null,
                                null, null, null, Visibility.PRIVATE, null, null, null, null))
                .getBody()
                .id();

        ResponseEntity<Map<String, Object>> response =
                authedMap(stranger, HttpMethod.GET, "/api/v1/records/" + recordId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("공개 프로필에는 PUBLIC 기록만 노출된다 (F-03-07)")
    void publicProfileShowsOnlyPublicRecords() {
        AuthResponse owner = signupNewUser();
        String handle = owner.user().handle();

        stubBook("1111111111111", "공개 기록 책");
        stubBook("2222222222222", "비공개 기록 책");
        createRecord(owner, requestFor("1111111111111", Visibility.PUBLIC));
        createRecord(owner, requestFor("2222222222222", Visibility.PRIVATE));

        ResponseEntity<RecordPage> response = rest.getForEntity(
                "/api/v1/users/@" + handle + "/records", RecordPage.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().get(0).content().title()).isEqualTo("공개 기록 책");
    }

    @Test
    @DisplayName("커서 페이지네이션이 중복·누락 없이 전체를 순회한다 (F-04-01)")
    void cursorPaginationCoversAllRecords() {
        AuthResponse me = signupNewUser();
        int total = 7;
        for (int i = 0; i < total; i++) {
            String isbn = "900000000000" + i;
            stubBook(isbn, "책 " + i);
            createRecord(me, requestFor(isbn, Visibility.PUBLIC));
        }

        List<Long> collected = new ArrayList<>();
        String cursor = "";
        int pages = 0;
        do {
            RecordPage page = cursor.isEmpty() ? timeline(me, "limit", "3") : timeline(me, "limit", "3", "cursor", cursor);
            page.items().forEach(item -> collected.add(item.id()));
            cursor = page.nextCursor() == null ? "" : page.nextCursor();
            pages++;
        } while (!cursor.isEmpty() && pages < 10);

        assertThat(collected).hasSize(total);
        assertThat(collected).doesNotHaveDuplicates();
        assertThat(pages).isEqualTo(3); // 3 + 3 + 1
    }

    @Test
    @DisplayName("잘못된 커서는 에러 대신 첫 페이지로 처리한다")
    void invalidCursorFallsBackToFirstPage() {
        AuthResponse me = signupNewUser();
        createRecord(me, simpleRequest(RecordStatus.DONE));

        RecordPage page = timeline(me, "cursor", "not-a-real-cursor");

        assertThat(page.items()).hasSize(1);
    }

    @Test
    @DisplayName("무드와 타입으로 타임라인을 필터링할 수 있다 (F-04-02)")
    void timelineFilters() {
        AuthResponse me = signupNewUser();
        stubBook("3333333333333", "새벽에 읽은 책");
        stubBook("4444444444444", "집중해서 읽은 책");

        createRecord(me, new CreateRecordRequest(
                ProviderType.NAVER_BOOK, "3333333333333", RecordStatus.DONE, new BigDecimal("5.0"),
                List.of("새벽"), null, null, Visibility.PUBLIC, null, null, null, null));
        createRecord(me, new CreateRecordRequest(
                ProviderType.NAVER_BOOK, "4444444444444", RecordStatus.DONE, new BigDecimal("3.0"),
                List.of("집중"), null, null, Visibility.PUBLIC, null, null, null, null));

        assertThat(timeline(me, "moods", "새벽").items())
                .singleElement()
                .satisfies(r -> assertThat(r.content().title()).isEqualTo("새벽에 읽은 책"));

        assertThat(timeline(me, "minRating", "4").items()).hasSize(1);
        assertThat(timeline(me, "types", "MOVIE").items()).isEmpty();
        assertThat(timeline(me, "status", "WANT").items()).isEmpty();
        // "#새벽" 처럼 입력해도 정규화되어 같은 결과가 나온다 (불변식 5)
        assertThat(timeline(me, "moods", "#새벽").items()).hasSize(1);
    }

    // ── helpers ─────────────────────────────────────────────

    private ResponseEntity<RecordResponse> createRecord(AuthResponse auth, CreateRecordRequest request) {
        return authed(auth, HttpMethod.POST, "/api/v1/records", request, RecordResponse.class);
    }

    /**
     * RestTemplate 의 문자열 URL 은 템플릿으로 취급되어 한 번 더 인코딩된다.
     * 값을 직접 인코딩한 URI 를 넘겨 이중 인코딩을 피한다.
     */
    private RecordPage timeline(AuthResponse auth, String... keyValues) {
        StringBuilder query = new StringBuilder("/api/v1/records/me?_=1");
        for (int i = 0; i < keyValues.length; i += 2) {
            query.append('&')
                    .append(keyValues[i])
                    .append('=')
                    .append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
        }
        ResponseEntity<RecordPage> response = rest.exchange(
                URI.create(rest.getRootUri() + query.toString()),
                HttpMethod.GET,
                new HttpEntity<>(bearer(auth)),
                new ParameterizedTypeReference<RecordPage>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private CreateRecordRequest simpleRequest(RecordStatus status) {
        return new CreateRecordRequest(
                ProviderType.NAVER_BOOK, "9788937473135", status, null, null, null, null, null,
                null, null, null, null);
    }

    private CreateRecordRequest requestFor(String isbn, Visibility visibility) {
        return new CreateRecordRequest(
                ProviderType.NAVER_BOOK, isbn, RecordStatus.DONE, null, null, null, null,
                visibility, null, null, null, null);
    }

    private void stubBook(String isbn, String title) {
        WIREMOCK.stubFor(get(urlPathMatching("/v1/search/book.*"))
                .withQueryParam("d_isbn", com.github.tomakehurst.wiremock.client.WireMock.equalTo(isbn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s","author":"저자","isbn":"%s",
                                  "publisher":"출판사","pubdate":"20200101","image":"","description":""}]}
                                """.formatted(title, isbn))));
    }
}
