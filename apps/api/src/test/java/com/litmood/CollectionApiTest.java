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
import com.litmood.domain.model.Visibility;
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.CollectionDtos.AddCollectionItemRequest;
import com.litmood.interfaces.dto.CollectionDtos.CollectionResponse;
import com.litmood.interfaces.dto.CollectionDtos.CreateCollectionRequest;
import com.litmood.interfaces.dto.CollectionDtos.ReorderItemsRequest;
import com.litmood.interfaces.dto.CollectionDtos.UpdateCollectionItemRequest;
import com.litmood.interfaces.dto.CollectionDtos.UpdateCollectionRequest;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.support.AuthenticatedTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** F-05·F-07-01 — 컬렉션 CRUD, 정렬, 공유, 무드별 탐색. */
class CollectionApiTest extends AuthenticatedTest {

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

    private static final java.util.concurrent.atomic.AtomicInteger ISBN_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(1000);

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
    }

    /**
     * 테스트들이 하나의 Postgres 컨테이너를 공유한다.
     * 콘텐츠 스냅샷은 (provider, externalId) 로 재사용되므로(F-02-05, 의도된 동작),
     * ISBN 이 겹치면 먼저 저장된 제목이 그대로 나와 스텁이 무시된다.
     * 테스트마다 고유한 ISBN 을 발급해 격리한다.
     */
    private String nextIsbn() {
        return "978%010d".formatted(ISBN_SEQ.incrementAndGet());
    }

    @Test
    @DisplayName("컬렉션을 만들면 제목이 읽히는 공유용 slug 가 생성된다")
    void createGeneratesReadableSlug() {
        AuthResponse me = signupNewUser();

        ResponseEntity<CollectionResponse> response = authed(
                me, HttpMethod.POST, "/api/v1/collections",
                new CreateCollectionRequest("비 오는 날 듣는 앨범", "창밖을 보며", Visibility.PUBLIC),
                CollectionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().slug()).startsWith("비-오는-날-듣는-앨범-");
        assertThat(response.getBody().itemCount()).isZero();
        assertThat(response.getBody().ownerHandle()).isEqualTo(me.user().handle());
    }

    @Test
    @DisplayName("같은 제목으로 여러 번 만들어도 slug 가 충돌하지 않는다")
    void duplicateTitlesGetDistinctSlugs() {
        AuthResponse me = signupNewUser();

        String first = createCollection(me, "같은 제목").slug();
        String second = createCollection(me, "같은 제목").slug();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("기록하지 않은 콘텐츠도 컬렉션에 담을 수 있다 (F-05 설계 원칙)")
    void canAddUnrecordedContent() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "새벽 목록").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "노르웨이의 숲");

        CollectionResponse updated = addItem(me, slug, isbn, "1장부터");

        assertThat(updated.itemCount()).isEqualTo(1);
        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.content().title()).isEqualTo("노르웨이의 숲");
            assertThat(item.note()).isEqualTo("1장부터");
            assertThat(item.position()).isZero();
        });
        // 기록은 만들어지지 않았다 — 컬렉션은 기록과 독립적이다
        ResponseEntity<Map<String, Object>> timeline =
                authedMap(me, HttpMethod.GET, "/api/v1/records/me", null);
        assertThat(timeline.getBody()).containsEntry("totalCount", 0);
    }

    @Test
    @DisplayName("같은 콘텐츠를 두 번 담으면 거부된다")
    void duplicateItemRejected() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "목록").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "노르웨이의 숲");
        addItem(me, slug, isbn, null);

        ResponseEntity<Map<String, Object>> second = authedMap(
                me, HttpMethod.POST, "/api/v1/collections/" + slug + "/items",
                new AddCollectionItemRequest(ProviderType.NAVER_BOOK, isbn, null));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("순서를 바꾸면 전달한 순서대로 position 이 다시 매겨진다")
    void reorderRewritesPositions() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "정렬 테스트").slug();

        String first = nextIsbn(), second = nextIsbn(), third = nextIsbn();
        stubBook(first, "첫째");
        stubBook(second, "둘째");
        stubBook(third, "셋째");
        addItem(me, slug, first, null);
        addItem(me, slug, second, null);
        CollectionResponse before = addItem(me, slug, third, null);

        assertThat(before.items()).extracting(i -> i.content().title())
                .containsExactly("첫째", "둘째", "셋째");

        List<Long> reversed = before.items().stream()
                .map(i -> i.content().id())
                .sorted(java.util.Comparator.reverseOrder())
                .toList();

        CollectionResponse after = authed(
                me, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/order",
                new ReorderItemsRequest(reversed), CollectionResponse.class).getBody();

        assertThat(after.items()).extracting(i -> i.content().title())
                .containsExactly("셋째", "둘째", "첫째");
        assertThat(after.items()).extracting("position").containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("중간 아이템을 제거해도 position 에 구멍이 남지 않는다")
    void removeResequencesPositions() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "제거 테스트").slug();

        String first = nextIsbn(), second = nextIsbn(), third = nextIsbn();
        stubBook(first, "첫째");
        stubBook(second, "둘째");
        stubBook(third, "셋째");
        addItem(me, slug, first, null);
        CollectionResponse withSecond = addItem(me, slug, second, null);
        addItem(me, slug, third, null);

        Long middleId = withSecond.items().get(1).content().id();
        CollectionResponse after = authed(
                me, HttpMethod.DELETE, "/api/v1/collections/" + slug + "/items/" + middleId,
                null, CollectionResponse.class).getBody();

        assertThat(after.items()).extracting(i -> i.content().title()).containsExactly("첫째", "셋째");
        assertThat(after.items()).extracting("position").containsExactly(0, 1);
        assertThat(after.itemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("커버를 지정하지 않으면 첫 아이템의 표지가 대표 이미지가 된다")
    void coverFallsBackToFirstItem() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "커버 없음").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "노르웨이의 숲");

        CollectionResponse updated = addItem(me, slug, isbn, null);

        assertThat(updated.coverUrl()).isEqualTo("https://cover.example/" + isbn + ".jpg");
    }

    @Test
    @DisplayName("공개 컬렉션은 비로그인 사용자도 볼 수 있다 — 공유가 유입 경로다")
    void publicCollectionReadableAnonymously() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "공개 목록").slug();

        ResponseEntity<CollectionResponse> response =
                rest.getForEntity("/api/v1/collections/" + slug, CollectionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo("공개 목록");
    }

    @Test
    @DisplayName("비공개 컬렉션은 타인에게 404 로 존재를 숨긴다")
    void privateCollectionHidden() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        CollectionResponse created = createCollection(owner, "비밀 목록");
        authed(owner, HttpMethod.PATCH, "/api/v1/collections/" + created.slug(),
                new UpdateCollectionRequest(null, null, null, Visibility.PRIVATE), CollectionResponse.class);

        assertThat(rest.getForEntity("/api/v1/collections/" + created.slug(), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(authedMap(stranger, HttpMethod.GET, "/api/v1/collections/" + created.slug(), null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("본인만 컬렉션을 수정할 수 있다")
    void onlyOwnerCanModify() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        String slug = createCollection(owner, "내 목록").slug();

        assertThat(authedMap(stranger, HttpMethod.PATCH, "/api/v1/collections/" + slug,
                        new UpdateCollectionRequest("가로채기", null, null, null))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("공개 프로필의 컬렉션 목록에는 공개 컬렉션만 노출된다")
    void profileListsOnlyPublicCollections() {
        AuthResponse me = signupNewUser();
        createCollection(me, "공개 목록");
        CollectionResponse hidden = createCollection(me, "숨긴 목록");
        authed(me, HttpMethod.PATCH, "/api/v1/collections/" + hidden.slug(),
                new UpdateCollectionRequest(null, null, null, Visibility.PRIVATE), CollectionResponse.class);

        ResponseEntity<List<Map<String, Object>>> anonymous = rest.exchange(
                "/api/v1/users/@" + me.user().handle() + "/collections",
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertThat(anonymous.getBody()).hasSize(1);
        assertThat(anonymous.getBody().get(0)).containsEntry("title", "공개 목록");

        // 본인이 조회하면 비공개도 포함된다
        ResponseEntity<List<Map<String, Object>>> self = rest.exchange(
                "/api/v1/users/@" + me.user().handle() + "/collections",
                HttpMethod.GET, new org.springframework.http.HttpEntity<>(bearer(me)),
                new ParameterizedTypeReference<>() {});
        assertThat(self.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("무드별 탐색은 공개 기록만 집계해 랭킹을 낸다 (F-07-01)")
    void moodDiscoveryRanksPublicRecordsOnly() {
        // 다른 테스트의 "새벽" 기록과 섞이지 않도록 이 테스트 전용 무드를 쓴다
        String mood = "새벽테스트" + ISBN_SEQ.incrementAndGet();
        String popular = nextIsbn(), rare = nextIsbn(), hidden = nextIsbn();
        stubBook(popular, "많이 읽힌 책");
        stubBook(rare, "덜 읽힌 책");
        stubBook(hidden, "비공개로만 기록된 책");

        // 같은 콘텐츠를 서로 다른 사용자가 같은 무드로 기록
        record(signupNewUser(), popular, List.of(mood), new BigDecimal("5.0"), Visibility.PUBLIC);
        record(signupNewUser(), popular, List.of(mood), new BigDecimal("4.0"), Visibility.PUBLIC);
        record(signupNewUser(), rare, List.of(mood), new BigDecimal("3.0"), Visibility.PUBLIC);
        // PRIVATE 은 집계에서 제외된다 (불변식 3)
        record(signupNewUser(), hidden, List.of(mood), new BigDecimal("5.0"), Visibility.PRIVATE);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/moods/{mood}/contents", HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Object>>() {}, mood);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents = (List<Map<String, Object>>) response.getBody().get("contents");

        assertThat(contents).hasSize(2); // 비공개 기록의 콘텐츠는 빠진다
        assertThat(contents.get(0)).containsEntry("recordCount", 2).containsEntry("averageRating", 4.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> moodInfo = (Map<String, Object>) response.getBody().get("mood");
        // 자유 입력 무드이므로 색은 없고, 이름은 정규화되어 돌아온다
        assertThat(moodInfo).containsEntry("name", mood.toLowerCase());
    }

    @Test
    @DisplayName("아무도 쓰지 않은 무드는 404 가 아니라 빈 결과로 응답한다")
    void unusedMoodReturnsEmpty() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/moods/아무도안쓴무드/contents", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("contents")).isEmpty();
    }

    @Test
    @DisplayName("담은 이유를 나중에 고칠 수 있다 (F-05-03, 이슈 #7)")
    void itemNoteCanBeEdited() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "노트 목록").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "노트 붙일 책");
        Long contentId = addItem(me, slug, isbn, "처음 담을 때의 메모")
                .items()
                .get(0)
                .content()
                .id();

        ResponseEntity<CollectionResponse> response = authed(
                me, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/" + contentId,
                new UpdateCollectionItemRequest("다시 읽고 고친 메모"), CollectionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items().get(0).note()).isEqualTo("다시 읽고 고친 메모");
    }

    @Test
    @DisplayName("노트를 빈 문자열로 보내면 지워진다 — 기록의 규칙과 같다")
    void emptyNoteClearsIt() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "노트 지우기").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "메모 지울 책");
        Long contentId = addItem(me, slug, isbn, "지워질 메모").items().get(0).content().id();

        CollectionResponse updated = authed(
                        me, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/" + contentId,
                        new UpdateCollectionItemRequest("   "), CollectionResponse.class)
                .getBody();

        assertThat(updated.items().get(0).note()).isNull();
    }

    @Test
    @DisplayName("담기지 않은 콘텐츠의 노트를 고치려 하면 404")
    void noteOnMissingItemIsNotFound() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "빈 목록").slug();

        assertThat(authedMap(me, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/999999",
                        new UpdateCollectionItemRequest("없는 아이템"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("남의 컬렉션 노트는 고칠 수 없다")
    void onlyOwnerCanEditNote() {
        AuthResponse owner = signupNewUser();
        AuthResponse stranger = signupNewUser();
        String slug = createCollection(owner, "남의 목록").slug();
        String isbn = nextIsbn();
        stubBook(isbn, "남의 책");
        Long contentId = addItem(owner, slug, isbn, null).items().get(0).content().id();

        assertThat(authedMap(stranger, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/" + contentId,
                        new UpdateCollectionItemRequest("가로채기"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("아이템 노트 경로가 순서 변경 경로를 가리지 않는다")
    void itemNotePathDoesNotShadowReorder() {
        AuthResponse me = signupNewUser();
        String slug = createCollection(me, "순서 유지").slug();
        String first = nextIsbn();
        String second = nextIsbn();
        stubBook(first, "첫째");
        stubBook(second, "둘째");
        addItem(me, slug, first, null);
        CollectionResponse before = addItem(me, slug, second, null);
        List<Long> reversed = List.of(
                before.items().get(1).content().id(), before.items().get(0).content().id());

        // /items/{contentId} 를 뒤에 붙였으므로 /items/order 가 여전히 리터럴로 잡혀야 한다
        CollectionResponse after = authed(
                        me, HttpMethod.PATCH, "/api/v1/collections/" + slug + "/items/order",
                        new ReorderItemsRequest(reversed), CollectionResponse.class)
                .getBody();

        assertThat(after.items()).extracting(i -> i.content().title()).containsExactly("둘째", "첫째");
    }

    @Test
    @DisplayName("제목만 고치는 요청이 설명을 지우지 않는다 — 넣지 않은 필드는 그대로다")
    void partialUpdateKeepsOtherFields() {
        AuthResponse me = signupNewUser();
        CollectionResponse created = authed(me, HttpMethod.POST, "/api/v1/collections",
                        new CreateCollectionRequest("원래 제목", "원래 설명", Visibility.FOLLOWERS),
                        CollectionResponse.class)
                .getBody();

        CollectionResponse updated = authed(me, HttpMethod.PATCH, "/api/v1/collections/" + created.slug(),
                        new UpdateCollectionRequest("고친 제목", null, null, null), CollectionResponse.class)
                .getBody();

        assertThat(updated.title()).isEqualTo("고친 제목");
        assertThat(updated.description()).isEqualTo("원래 설명");
        assertThat(updated.visibility()).isEqualTo(Visibility.FOLLOWERS);
    }

    @Test
    @DisplayName("설명은 빈 문자열로 지운다 — 커버도 지우면 다시 첫 아이템을 따라간다")
    void emptyStringClearsDescriptionAndCover() {
        AuthResponse me = signupNewUser();
        CollectionResponse created = authed(me, HttpMethod.POST, "/api/v1/collections",
                        new CreateCollectionRequest("지우기", "지워질 설명", Visibility.PUBLIC),
                        CollectionResponse.class)
                .getBody();
        String isbn = nextIsbn();
        stubBook(isbn, "표지 있는 책");
        addItem(me, created.slug(), isbn, null);

        CollectionResponse pinned = authed(me, HttpMethod.PATCH, "/api/v1/collections/" + created.slug(),
                        new UpdateCollectionRequest(null, "", "https://cover.example/직접-지정.jpg", null),
                        CollectionResponse.class)
                .getBody();
        assertThat(pinned.description()).isNull();
        assertThat(pinned.coverUrl()).isEqualTo("https://cover.example/직접-지정.jpg");
        assertThat(pinned.coverPinned()).isTrue();

        CollectionResponse unpinned = authed(me, HttpMethod.PATCH, "/api/v1/collections/" + created.slug(),
                        new UpdateCollectionRequest(null, null, "", null), CollectionResponse.class)
                .getBody();
        assertThat(unpinned.coverPinned()).isFalse();
        assertThat(unpinned.coverUrl()).isEqualTo("https://cover.example/" + isbn + ".jpg");
    }

    // ── helpers ─────────────────────────────────────────────

    private CollectionResponse createCollection(AuthResponse auth, String title) {
        return authed(auth, HttpMethod.POST, "/api/v1/collections",
                        new CreateCollectionRequest(title, null, Visibility.PUBLIC), CollectionResponse.class)
                .getBody();
    }

    private CollectionResponse addItem(AuthResponse auth, String slug, String isbn, String note) {
        ResponseEntity<CollectionResponse> response = authed(
                auth, HttpMethod.POST, "/api/v1/collections/" + slug + "/items",
                new AddCollectionItemRequest(ProviderType.NAVER_BOOK, isbn, note), CollectionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    @DisplayName("컬렉션 좋아요가 응답의 likeCount·likedByMe 에 반영된다 (F-06-03)")
    void collectionLikeReflectedInResponse() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        String slug = createCollection(owner, "좋아요 대상").slug();

        CollectionResponse before =
                authed(liker, HttpMethod.GET, "/api/v1/collections/" + slug, null, CollectionResponse.class)
                        .getBody();
        assertThat(before.likeCount()).isZero();
        assertThat(before.likedByMe()).isFalse();

        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        CollectionResponse after =
                authed(liker, HttpMethod.GET, "/api/v1/collections/" + slug, null, CollectionResponse.class)
                        .getBody();
        assertThat(after.likeCount()).isEqualTo(1);
        assertThat(after.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("likedByMe 는 조회자마다 다르다 — 남이 누른 좋아요는 내 것이 아니다")
    void likedByMeIsPerViewer() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        AuthResponse other = signupNewUser();
        String slug = createCollection(owner, "조회자별 상태").slug();
        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        CollectionResponse seenByOther =
                authed(other, HttpMethod.GET, "/api/v1/collections/" + slug, null, CollectionResponse.class)
                        .getBody();

        assertThat(seenByOther.likeCount()).isEqualTo(1);
        assertThat(seenByOther.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("비로그인 조회의 likedByMe 는 언제나 false — 이 응답은 캐시된다")
    void anonymousViewNeverLiked() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        String slug = createCollection(owner, "익명 조회").slug();
        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        CollectionResponse anonymous =
                rest.getForEntity("/api/v1/collections/" + slug, CollectionResponse.class).getBody();

        assertThat(anonymous.likeCount()).isEqualTo(1);
        assertThat(anonymous.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("같은 컬렉션을 두 번 좋아요해도 개수는 하나다")
    void likingTwiceCountsOnce() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        String slug = createCollection(owner, "멱등 좋아요").slug();

        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);
        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        assertThat(authed(liker, HttpMethod.GET, "/api/v1/collections/" + slug, null, CollectionResponse.class)
                        .getBody()
                        .likeCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요를 취소하면 개수가 줄고 likedByMe 가 풀린다")
    void unlikeRestoresState() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        String slug = createCollection(owner, "취소 대상").slug();
        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        authed(liker, HttpMethod.DELETE, "/api/v1/collections/" + slug + "/like", null, Map.class);

        CollectionResponse after =
                authed(liker, HttpMethod.GET, "/api/v1/collections/" + slug, null, CollectionResponse.class)
                        .getBody();
        assertThat(after.likeCount()).isZero();
        assertThat(after.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("프로필의 컬렉션 목록에도 좋아요 수가 실린다")
    void summaryCarriesLikeCount() {
        AuthResponse owner = signupNewUser();
        AuthResponse liker = signupNewUser();
        String slug = createCollection(owner, "목록에 뜨는 좋아요").slug();
        authed(liker, HttpMethod.POST, "/api/v1/collections/" + slug + "/like", null, Map.class);

        ResponseEntity<List<Map<String, Object>>> list = rest.exchange(
                "/api/v1/users/@" + owner.user().handle() + "/collections",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertThat(list.getBody())
                .anySatisfy(summary -> {
                    assertThat(summary.get("slug")).isEqualTo(slug);
                    assertThat(summary.get("likeCount")).isEqualTo(1);
                });
    }

    private void record(
            AuthResponse auth, String isbn, List<String> moods, BigDecimal rating, Visibility visibility) {
        authed(auth, HttpMethod.POST, "/api/v1/records",
                new CreateRecordRequest(ProviderType.NAVER_BOOK, isbn, RecordStatus.DONE, rating,
                        moods, null, null, visibility, null, null, null, null),
                Map.class);
    }

    private void stubBook(String isbn, String title) {
        WIREMOCK.stubFor(get(urlPathMatching("/v1/search/book.*"))
                .withQueryParam("d_isbn", equalTo(isbn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"items":[{"title":"%s","author":"저자","isbn":"%s","publisher":"출판사",
                                  "pubdate":"20200101","image":"https://cover.example/%s.jpg","description":""}]}
                                """.formatted(title, isbn, isbn))));
    }
}
