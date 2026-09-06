package com.litmood;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Report;
import com.litmood.domain.model.Visibility;
import com.litmood.interfaces.dto.AdminDtos.AdminReportPage;
import com.litmood.interfaces.dto.AdminDtos.AdminReportResponse;
import com.litmood.interfaces.dto.AdminDtos.ReportResolutionRequest;
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.AuthDtos.SignupRequest;
import com.litmood.interfaces.dto.CollectionDtos.CollectionResponse;
import com.litmood.interfaces.dto.CollectionDtos.CreateCollectionRequest;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.interfaces.dto.RecordDtos.RecordResponse;
import com.litmood.interfaces.dto.SocialDtos.ReportRequest;
import com.litmood.support.AuthenticatedTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 운영 신고 처리 (#28).
 *
 * <p>큐는 저장소 전체를 보므로 다른 테스트가 남긴 신고도 함께 나온다
 * (컨테이너를 공유한다 — CLAUDE.md). 총계는 정확한 값이 아니라 하한으로 확인하고,
 * 항목은 이 테스트가 만든 id 를 찾아서 검증한다.
 */
class AdminReportApiTest extends AuthenticatedTest {

    /** 이 테스트 컨텍스트의 운영자. 설정에 적힌 핸들로 가입해야 운영자가 된다. */
    private static final String ADMIN_HANDLE = "litmood_ops";

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final AtomicInteger ISBN_SEQ = new AtomicInteger(9000);

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        registry.add("litmood.admin.handles", () -> ADMIN_HANDLE);
        String base = "http://localhost:" + WIREMOCK.port();
        registry.add("litmood.provider.naver.base-url", () -> base);
        registry.add("litmood.provider.naver.client-id", () -> "test-id");
        registry.add("litmood.provider.naver.client-secret", () -> "test-secret");
    }

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    // ── 접근 통제 ───────────────────────────────────────────

    @Test
    @DisplayName("운영자가 아니면 403 이 아니라 404 — 403 은 관리자 화면의 존재를 알려준다")
    void nonAdminGetsNotFound() {
        AuthResponse user = signupNewUser();

        assertThat(authedMap(user, HttpMethod.GET, "/api/v1/admin/reports", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("로그인하지 않으면 401 — 다른 보호된 엔드포인트와 구분되지 않는다")
    void anonymousGetsUnauthorized() {
        assertThat(rest.getForEntity("/api/v1/admin/reports", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("설정에 적힌 핸들로 가입한 사용자만 운영자다 — 응답의 admin 플래그로 화면이 판단한다")
    void adminFlagComesFromConfiguration() {
        assertThat(signupNewUser().user().admin()).isFalse();
        assertThat(admin().user().admin()).isTrue();
    }

    // ── 큐 ──────────────────────────────────────────────────

    @Test
    @DisplayName("큐에는 대상의 이름이 함께 실린다 — targetId 만 주면 운영자가 다시 조회해야 한다")
    void queueCarriesReadableTargets() {
        AuthResponse admin = admin();
        AuthResponse reporter = signupNewUser();
        AuthResponse owner = signupNewUser();

        RecordResponse record = record(owner, isbnFor("신고당한 책"));
        report(reporter, "/api/v1/records/" + record.id() + "/report", Report.ReportReason.SPOILER);

        String slug = collection(owner, "신고당한 컬렉션");
        report(reporter, "/api/v1/collections/" + slug + "/report", Report.ReportReason.SPAM);

        report(reporter, "/api/v1/users/@" + owner.user().handle() + "/report", Report.ReportReason.ABUSE);

        AdminReportPage page = queue(admin, "?status=PENDING&limit=50");

        AdminReportResponse onRecord = find(page, Report.ReportTarget.RECORD, record.id());
        assertThat(onRecord.target().label()).isEqualTo("신고당한 책");
        assertThat(onRecord.target().handle()).isEqualTo(owner.user().handle());
        assertThat(onRecord.target().deleted()).isFalse();
        assertThat(onRecord.reporterHandle()).isEqualTo(reporter.user().handle());

        AdminReportResponse onCollection = page.items().stream()
                .filter(item -> slug.equals(item.target().slug()))
                .findFirst()
                .orElseThrow();
        assertThat(onCollection.target().label()).isEqualTo("신고당한 컬렉션");
        assertThat(onCollection.target().handle()).isEqualTo(owner.user().handle());

        AdminReportResponse onUser = find(page, Report.ReportTarget.USER, owner.user().id());
        assertThat(onUser.target().label()).isEqualTo(owner.user().nickname());
        assertThat(onUser.target().handle()).isEqualTo(owner.user().handle());
    }

    @Test
    @DisplayName("같은 대상에 쌓인 신고 건수를 함께 준다 — 여러 사람이 같은 것을 신고했다는 신호")
    void queueShowsHowManyReportsShareATarget() {
        AuthResponse admin = admin();
        AuthResponse owner = signupNewUser();
        RecordResponse record = record(owner, isbnFor("여러 번 신고당한 책"));

        report(signupNewUser(), "/api/v1/records/" + record.id() + "/report", Report.ReportReason.SPAM);
        report(signupNewUser(), "/api/v1/records/" + record.id() + "/report", Report.ReportReason.ABUSE);
        report(signupNewUser(), "/api/v1/records/" + record.id() + "/report", Report.ReportReason.SEXUAL);

        AdminReportPage page = queue(admin, "?status=PENDING&limit=50");

        assertThat(find(page, Report.ReportTarget.RECORD, record.id()).sameTargetCount())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("삭제된 대상도 큐에 남는다 — 조치할 것이 없다는 사실을 운영자가 알아야 한다")
    void deletedTargetIsMarkedNotHidden() {
        AuthResponse admin = admin();
        AuthResponse owner = signupNewUser();
        RecordResponse record = record(owner, isbnFor("지워질 책"));
        report(signupNewUser(), "/api/v1/records/" + record.id() + "/report", Report.ReportReason.SPAM);

        assertThat(authedMap(owner, HttpMethod.DELETE, "/api/v1/records/" + record.id(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(find(queue(admin, "?status=PENDING&limit=50"), Report.ReportTarget.RECORD, record.id())
                        .target()
                        .deleted())
                .isTrue();
    }

    @Test
    @DisplayName("커서로 이어 받으면 같은 항목이 다시 나오지 않는다")
    void cursorDoesNotRepeatItems() {
        AuthResponse admin = admin();

        AdminReportPage first = queue(admin, "?limit=1");
        assertThat(first.items()).hasSize(1);
        assertThat(first.nextCursor()).isNotNull();

        AdminReportPage second = queue(admin, "?limit=1&cursor=" + first.nextCursor());
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().get(0).id()).isNotEqualTo(first.items().get(0).id());
    }

    // ── 처리 ────────────────────────────────────────────────

    @Test
    @DisplayName("처리하면 PENDING 큐에서 빠지고 처리 시각이 남는다")
    void resolvingRemovesFromPendingQueue() {
        AuthResponse admin = admin();
        AuthResponse owner = signupNewUser();
        String slug = collection(owner, "처리될 컬렉션");
        report(signupNewUser(), "/api/v1/collections/" + slug + "/report", Report.ReportReason.SPAM);

        Long reportId = bySlug(queue(admin, "?status=PENDING&limit=50"), slug).id();

        ResponseEntity<AdminReportResponse> resolved = authed(
                admin,
                HttpMethod.PATCH,
                "/api/v1/admin/reports/" + reportId,
                new ReportResolutionRequest(Report.ReportStatus.REVIEWED),
                AdminReportResponse.class);

        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().status()).isEqualTo(Report.ReportStatus.REVIEWED);
        assertThat(resolved.getBody().resolvedAt()).isNotNull();

        assertThat(queue(admin, "?status=PENDING&limit=50").items())
                .noneMatch(item -> item.id().equals(reportId));
        assertThat(queue(admin, "?status=REVIEWED&limit=50").items())
                .anyMatch(item -> item.id().equals(reportId));
    }

    @Test
    @DisplayName("이미 처리된 신고를 다시 처리하면 409")
    void resolvingTwiceConflicts() {
        AuthResponse admin = admin();
        AuthResponse owner = signupNewUser();
        String slug = collection(owner, "두 번 처리될 컬렉션");
        report(signupNewUser(), "/api/v1/collections/" + slug + "/report", Report.ReportReason.SPAM);

        Long reportId = bySlug(queue(admin, "?status=PENDING&limit=50"), slug).id();
        String path = "/api/v1/admin/reports/" + reportId;

        assertThat(authedMap(admin, HttpMethod.PATCH, path,
                                new ReportResolutionRequest(Report.ReportStatus.DISMISSED))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(authedMap(admin, HttpMethod.PATCH, path,
                                new ReportResolutionRequest(Report.ReportStatus.REVIEWED))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("운영자가 아니면 처리도 404")
    void nonAdminCannotResolve() {
        assertThat(authedMap(
                                signupNewUser(),
                                HttpMethod.PATCH,
                                "/api/v1/admin/reports/1",
                                new ReportResolutionRequest(Report.ReportStatus.REVIEWED))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("없는 신고를 처리하면 404")
    void resolvingUnknownReport() {
        assertThat(authedMap(
                                admin(),
                                HttpMethod.PATCH,
                                "/api/v1/admin/reports/99999999",
                                new ReportResolutionRequest(Report.ReportStatus.REVIEWED))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 도구 ────────────────────────────────────────────────

    /** 운영자는 설정에 적힌 핸들을 가져야 하므로 signupNewUser() 를 쓸 수 없다. */
    private AuthResponse admin() {
        ResponseEntity<AuthResponse> signup = rest.postForEntity(
                "/api/v1/auth/signup",
                new SignupRequest(ADMIN_HANDLE + "@litmood.test", "password123", ADMIN_HANDLE, "운영자"),
                AuthResponse.class);
        if (signup.getStatusCode() == HttpStatus.CONFLICT) {
            // 앞선 테스트가 이미 만들었다 — 컨테이너를 공유하므로 로그인만 한다
            return rest.postForEntity(
                            "/api/v1/auth/login",
                            new com.litmood.interfaces.dto.AuthDtos.LoginRequest(
                                    ADMIN_HANDLE + "@litmood.test", "password123"),
                            AuthResponse.class)
                    .getBody();
        }
        return signup.getBody();
    }

    private AdminReportPage queue(AuthResponse admin, String query) {
        ResponseEntity<AdminReportPage> response =
                authed(admin, HttpMethod.GET, "/api/v1/admin/reports" + query, null, AdminReportPage.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private AdminReportResponse find(AdminReportPage page, Report.ReportTarget type, Long targetId) {
        return page.items().stream()
                .filter(item -> item.target().type() == type && item.target().id().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " " + targetId + " 신고가 큐에 없다"));
    }

    private AdminReportResponse bySlug(AdminReportPage page, String slug) {
        return page.items().stream()
                .filter(item -> slug.equals(item.target().slug()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(slug + " 신고가 큐에 없다"));
    }

    private void report(AuthResponse reporter, String path, Report.ReportReason reason) {
        assertThat(authedMap(reporter, HttpMethod.POST, path, new ReportRequest(reason, null))
                        .getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    private String collection(AuthResponse owner, String title) {
        return authed(
                        owner,
                        HttpMethod.POST,
                        "/api/v1/collections",
                        new CreateCollectionRequest(title, null, Visibility.PUBLIC),
                        CollectionResponse.class)
                .getBody()
                .slug();
    }

    private RecordResponse record(AuthResponse user, String isbn) {
        ResponseEntity<RecordResponse> response = authed(
                user,
                HttpMethod.POST,
                "/api/v1/records",
                new CreateRecordRequest(
                        ProviderType.NAVER_BOOK, isbn, RecordStatus.DONE, null, null, null, null,
                        Visibility.PUBLIC, null, null, null, null),
                RecordResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String isbnFor(String title) {
        String isbn = "978%010d".formatted(ISBN_SEQ.incrementAndGet());
        WIREMOCK.stubFor(get(urlPathMatching("/v1/search/book.*"))
                .withQueryParam("d_isbn", equalTo(isbn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {"items":[{"title":"%s","author":"저자","isbn":"%s","publisher":"출판사",
                                  "pubdate":"20200101","image":"","description":""}]}
                                """
                                        .formatted(title, isbn))));
        return isbn;
    }
}
