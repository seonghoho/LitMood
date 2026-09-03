package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Collection;
import com.litmood.domain.model.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** F-05 도메인 규칙 — slug 생성과 공개 범위. */
class CollectionDomainTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}-xxxxxx\"")
    @CsvSource({
        "'비 오는 날 듣는 앨범', 비-오는-날-듣는-앨범",
        "'Rainy Day Albums', rainy-day-albums",
        "'새벽 3시!!! 플레이리스트???', 새벽-3시-플레이리스트",
        "'   공백   투성이   ', 공백-투성이",
    })
    @DisplayName("slug 는 제목을 읽을 수 있게 유지하면서 랜덤 접미사로 유일성을 얻는다")
    void slugKeepsTitleReadable(String title, String expectedPrefix) {
        String slug = Collection.generateSlug(title);

        assertThat(slug).startsWith(expectedPrefix + "-");
        // 접미사가 붙어 같은 제목이라도 서로 다른 slug 가 된다
        assertThat(slug).isNotEqualTo(Collection.generateSlug(title));
    }

    @Test
    @DisplayName("제목이 기호뿐이면 기본 slug 로 대체된다")
    void symbolOnlyTitleFallsBack() {
        assertThat(Collection.generateSlug("!!!???")).startsWith("collection-");
    }

    @Test
    @DisplayName("아주 긴 제목도 slug 길이 제한(80) 안에 들어간다")
    void longTitleIsTruncated() {
        String slug = Collection.generateSlug("가".repeat(200));

        assertThat(slug.length()).isLessThanOrEqualTo(80);
    }

    @Test
    @DisplayName("PRIVATE 컬렉션은 비로그인·타인에게 보이지 않는다")
    void privateCollectionHidden() {
        Collection collection = Collection.create(1L, "s", "제목", null, Visibility.PRIVATE);

        assertThat(collection.isVisibleTo(1L, false)).isTrue();
        assertThat(collection.isVisibleTo(2L, true)).isFalse();
        assertThat(collection.isVisibleTo(null, false)).isFalse();
    }

    @Test
    @DisplayName("공개 컬렉션은 비로그인 사용자도 볼 수 있다 — 공유가 유입 경로다")
    void publicCollectionVisibleToAnonymous() {
        Collection collection = Collection.create(1L, "s", "제목", null, Visibility.PUBLIC);

        assertThat(collection.isVisibleTo(null, false)).isTrue();
    }

    @Test
    @DisplayName("공개 범위를 지정하지 않으면 전체 공개가 기본값이다")
    void defaultsToPublic() {
        assertThat(Collection.create(1L, "s", "제목", null, null).getVisibility())
                .isEqualTo(Visibility.PUBLIC);
    }
}
