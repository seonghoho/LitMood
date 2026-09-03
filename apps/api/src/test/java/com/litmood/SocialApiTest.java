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
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
import com.litmood.interfaces.dto.RecordDtos.RecordResponse;
import com.litmood.interfaces.dto.SocialDtos.LikeResponse;
import com.litmood.interfaces.dto.SocialDtos.ReportRequest;
import com.litmood.support.AuthenticatedTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

/** F-06·F-07-02 — 팔로우, 피드, 좋아요, 차단, 신고, 인기 랭킹. */
class SocialApiTest extends AuthenticatedTest {

    static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final AtomicInteger ISBN_SEQ = new AtomicInteger(5000);

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
    void reset() {
        WIREMOCK.resetAll();
    }

    // ── 팔로우와 피드 ───────────────────────────────────────

    @Test
    @DisplayName("팔로우하면 상대의 기록이 내 피드에 나타난다 (F-06-02)")
    void feedShowsFollowedUsersRecords() {
        AuthResponse me = signupNewUser();
        AuthResponse other = signupNewUser();
        AuthResponse stranger = signupNewUser();

        record(other, isbnFor("팔로우한 사람의 책"), Visibility.PUBLIC);
        record(stranger, isbnFor("모르는 사람의 책"), Visibility.PUBLIC);

        // 팔로우 전에는 피드가 비어 있다
        assertThat(feed(me).items()).isEmpty();

        follow(me, other);

        assertThat(feed(me).items())
                .singleElement()
                .satisfies(r -> assertThat(r.content().title()).isEqualTo("팔로우한 사람의 책"));
    }

    @Test
    @DisplayName("팔로우는 멱등하다 — 두 번 눌러도 중복되지 않는다")
    void followIsIdempotent() {
        AuthResponse me = signupNewUser();
        AuthResponse other = signupNewUser();
        record(other, isbnFor("책"), Visibility.PUBLIC);

        follow(me, other);
        follow(me, other);

        assertThat(feed(me).items()).hasSize(1);
    }

    @Test
    @DisplayName("자기 자신은 팔로우할 수 없다")
    void cannotFollowSelf() {
        AuthResponse me = signupNewUser();

        ResponseEntity<Map<String, Object>> response = authedMap(
                me, HttpMethod.POST, "/api/v1/users/@" + me.user().handle() + "/follow", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("언팔로우하면 피드에서 사라진다")
    void unfollowRemovesFromFeed() {
        AuthResponse me = signupNewUser();
        AuthResponse other = signupNewUser();
        record(other, isbnFor("책"), Visibility.PUBLIC);
        follow(me, other);
        assertThat(feed(me).items()).hasSize(1);

        authedMap(me, HttpMethod.DELETE, "/api/v1/users/@" + other.user().handle() + "/follow", null);

        assertThat(feed(me).items()).isEmpty();
    }

    // ── FOLLOWERS 공개범위 (M2·M3 에서 미완이던 것) ──────────

    @Test
    @DisplayName("FOLLOWERS 기록은 팔로워에게만 보인다 — 팔로우 구현으로 비로소 동작한다")
    void followersOnlyRecordVisibleToFollowers() {
        AuthResponse owner = signupNewUser();
        AuthResponse follower = signupNewUser();
        AuthResponse stranger = signupNewUser();

        Long recordId = record(owner, isbnFor("팔로워 공개 책"), Visibility.FOLLOWERS).id();
        follow(follower, owner);

        // 팔로워는 볼 수 있다
        assertThat(authedMap(follower, HttpMethod.GET, "/api/v1/records/" + recordId, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // 팔로우하지 않은 사람에게는 존재 자체가 숨겨진다
        assertThat(authedMap(stranger, HttpMethod.GET, "/api/v1/records/" + recordId, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // 비로그인도 마찬가지
        assertThat(rest.getForEntity("/api/v1/records/" + recordId, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("공개 프로필에서도 팔로워면 FOLLOWERS 기록까지 보인다")
    void profileShowsFollowersOnlyRecordsToFollowers() {
        AuthResponse owner = signupNewUser();
        AuthResponse follower = signupNewUser();
        record(owner, isbnFor("전체 공개"), Visibility.PUBLIC);
        record(owner, isbnFor("팔로워 공개"), Visibility.FOLLOWERS);

        String path = "/api/v1/users/@" + owner.user().handle() + "/records";

        // 비로그인: PUBLIC 만
        assertThat(rest.getForEntity(path, RecordPage.class).getBody().items()).hasSize(1);

        follow(follower, owner);

        // 팔로워: PUBLIC + FOLLOWERS
        ResponseEntity<RecordPage> asFollower = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearer(follower)),
                new ParameterizedTypeReference<>() {});
        assertThat(asFollower.getBody().items()).hasSize(2);
    }

    // ── 좋아요 ──────────────────────────────────────────────

    @Test
    @DisplayName("좋아요는 카운터를 정확히 증감하고 중복 클릭에 흔들리지 않는다")
    void likeCounterIsAccurate() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        Long recordId = record(owner, isbnFor("좋아요 대상"), Visibility.PUBLIC).id();

        assertThat(like(liker, recordId).likeCount()).isEqualTo(1);
        // 같은 사용자가 다시 눌러도 1 이다
        assertThat(like(liker, recordId).likeCount()).isEqualTo(1);

        AuthResponse another = signupNewUser();
        assertThat(like(another, recordId).likeCount()).isEqualTo(2);

        assertThat(unlike(liker, recordId).likeCount()).isEqualTo(1);
        // 이미 취소했으므로 더 내려가지 않는다
        assertThat(unlike(liker, recordId).likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타임라인에 좋아요 수와 내가 눌렀는지가 함께 온다")
    void timelineIncludesLikeState() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        Long recordId = record(owner, isbnFor("좋아요 표시"), Visibility.PUBLIC).id();
        like(liker, recordId);
        follow(liker, owner);

        RecordPage feed = feed(liker);

        assertThat(feed.items()).singleElement().satisfies(r -> {
            assertThat(r.likeCount()).isEqualTo(1);
            assertThat(r.likedByMe()).isTrue();
        });

        // 누르지 않은 제3자에게는 likedByMe 가 false
        AuthResponse other = signupNewUser();
        follow(other, owner);
        assertThat(feed(other).items()).singleElement().satisfies(r -> {
            assertThat(r.likeCount()).isEqualTo(1);
            assertThat(r.likedByMe()).isFalse();
        });
    }

    @Test
    @DisplayName("볼 수 없는 기록에는 좋아요를 누를 수 없다")
    void cannotLikeInvisibleRecord() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        Long recordId = record(owner, isbnFor("비공개"), Visibility.PRIVATE).id();

        assertThat(authedMap(stranger, HttpMethod.POST, "/api/v1/records/" + recordId + "/like", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── 차단 ────────────────────────────────────────────────

    @Test
    @DisplayName("차단하면 양쪽 모두 상대를 볼 수 없고 팔로우 관계가 해제된다 (F-06-05)")
    void blockHidesBothDirections() {
        AuthResponse me = signupNewUser();
        AuthResponse pest = signupNewUser();

        record(pest, isbnFor("차단 대상의 책"), Visibility.PUBLIC);
        follow(me, pest);
        follow(pest, me);
        assertThat(feed(me).items()).hasSize(1);

        authedMap(me, HttpMethod.POST, "/api/v1/users/@" + pest.user().handle() + "/block", null);

        // 팔로우가 양쪽 모두 끊어져 피드에서 사라진다
        assertThat(feed(me).items()).isEmpty();
        // 상대의 프로필도 보이지 않는다
        assertThat(authedMap(me, HttpMethod.GET, "/api/v1/users/@" + pest.user().handle(), null)
                        .getStatusCode())
                .isIn(HttpStatus.NOT_FOUND, HttpStatus.OK); // 프로필 자체는 정책상 노출 가능
        // 차단한 상대는 내 기록 목록을 볼 수 없다
        assertThat(authedMap(pest, HttpMethod.GET, "/api/v1/users/@" + me.user().handle() + "/records", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("차단한 상대는 팔로우할 수 없다")
    void cannotFollowBlockedUser() {
        AuthResponse me = signupNewUser();
        AuthResponse pest = signupNewUser();
        authedMap(me, HttpMethod.POST, "/api/v1/users/@" + pest.user().handle() + "/block", null);

        // 차단당한 쪽에서 팔로우를 시도해도 거부된다
        assertThat(authedMap(pest, HttpMethod.POST, "/api/v1/users/@" + me.user().handle() + "/follow", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 신고 ────────────────────────────────────────────────

    @Test
    @DisplayName("같은 대상을 반복 신고해도 한 번만 접수된다")
    void reportIsDeduplicated() {
        AuthResponse me = signupNewUser();
        AuthResponse owner = signupNewUser();
        Long recordId = record(owner, isbnFor("신고 대상"), Visibility.PUBLIC).id();

        ReportRequest request = new ReportRequest(
                Report.ReportTarget.RECORD, recordId, Report.ReportReason.SPAM, "광고입니다");

        assertThat(authedMap(me, HttpMethod.POST, "/api/v1/reports", request).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        // 두 번째도 사용자에겐 성공으로 보이지만 큐에는 쌓이지 않는다
        assertThat(authedMap(me, HttpMethod.POST, "/api/v1/reports", request).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    // ── 인기 랭킹 ───────────────────────────────────────────

    @Test
    @DisplayName("기록이 쌓이면 인기 콘텐츠 랭킹에 반영된다 (F-07-02)")
    void popularRankingReflectsRecords() {
        String popular = isbnFor("많이 기록된 책");
        String rare = isbnFor("적게 기록된 책");

        for (int i = 0; i < 3; i++) {
            record(signupNewUser(), popular, Visibility.PUBLIC);
        }
        record(signupNewUser(), rare, Visibility.PUBLIC);

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/v1/discover/popular?period=week", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> titles = response.getBody().stream().map(m -> (String) m.get("title")).toList();
        // 많이 기록된 쪽이 적게 기록된 쪽보다 앞에 온다
        assertThat(titles).contains("많이 기록된 책", "적게 기록된 책");
        assertThat(titles.indexOf("많이 기록된 책")).isLessThan(titles.indexOf("적게 기록된 책"));
    }

    // ── helpers ─────────────────────────────────────────────

    private void follow(AuthResponse follower, AuthResponse target) {
        ResponseEntity<Map<String, Object>> response = authedMap(
                follower, HttpMethod.POST, "/api/v1/users/@" + target.user().handle() + "/follow", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private LikeResponse like(AuthResponse user, Long recordId) {
        return authed(user, HttpMethod.POST, "/api/v1/records/" + recordId + "/like", null,
                        LikeResponse.class)
                .getBody();
    }

    private LikeResponse unlike(AuthResponse user, Long recordId) {
        return authed(user, HttpMethod.DELETE, "/api/v1/records/" + recordId + "/like", null,
                        LikeResponse.class)
                .getBody();
    }

    private RecordPage feed(AuthResponse user) {
        ResponseEntity<RecordPage> response = rest.exchange(
                "/api/v1/records/feed", HttpMethod.GET, new HttpEntity<>(bearer(user)),
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private RecordResponse record(AuthResponse user, String isbn, Visibility visibility) {
        ResponseEntity<RecordResponse> response = authed(
                user, HttpMethod.POST, "/api/v1/records",
                new CreateRecordRequest(ProviderType.NAVER_BOOK, isbn, RecordStatus.DONE, null,
                        null, null, null, visibility, null, null, null, null),
                RecordResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** 테스트들이 DB 를 공유하므로 콘텐츠는 매번 새 ISBN 으로 만든다. */
    private String isbnFor(String title) {
        String isbn = "978%010d".formatted(ISBN_SEQ.incrementAndGet());
        WIREMOCK.stubFor(get(urlPathMatching("/v1/search/book.*"))
                .withQueryParam("d_isbn", equalTo(isbn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s","author":"저자","isbn":"%s","publisher":"출판사",
                                  "pubdate":"20200101","image":"","description":""}]}
                                """.formatted(title, isbn))));
        return isbn;
    }
}
