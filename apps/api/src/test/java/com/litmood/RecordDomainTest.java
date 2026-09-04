package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Content;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.Mood;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.Visibility;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 도메인 불변식 단위 테스트 (docs/04-domain-model.md).
 * DB 나 스프링 컨텍스트 없이 규칙만 검증한다 — 빠르고, 규칙이 어디 사는지 분명해진다.
 */
class RecordDomainTest {

    @Test
    @DisplayName("불변식 2 — 아직 보지 않은(WANT) 콘텐츠에는 별점을 남길 수 없다")
    void wantStatusRejectsRating() {
        Record record = newRecord(RecordStatus.WANT);

        assertThatThrownBy(() -> record.changeRating(new BigDecimal("4.5")))
                .isInstanceOf(LitmoodException.class)
                .hasMessageContaining("별점");
    }

    @Test
    @DisplayName("DONE 으로 바꾸면 별점을 남길 수 있다")
    void doneStatusAllowsRating() {
        Record record = newRecord(RecordStatus.WANT);

        record.changeStatus(RecordStatus.DONE);
        record.changeRating(new BigDecimal("4.5"));

        assertThat(record.getRating()).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("DONE 에서 WANT 로 되돌리면 별점이 함께 사라져 불변식이 유지된다")
    void revertingToWantClearsRating() {
        Record record = newRecord(RecordStatus.DONE);
        record.changeRating(new BigDecimal("3.0"));

        record.changeStatus(RecordStatus.WANT);

        assertThat(record.getRating()).isNull();
    }

    @Test
    @DisplayName("무드는 최대 5개까지만 붙일 수 있다 (F-03-04)")
    void moodLimitEnforced() {
        Record record = newRecord(RecordStatus.DONE);
        Set<Mood> sixMoods = IntStream.range(0, 6)
                .mapToObj(i -> Mood.ofFreeform("무드" + i))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        assertThatThrownBy(() -> record.replaceMoods(sixMoods))
                .isInstanceOf(LitmoodException.class)
                .hasMessageContaining("5개");
    }

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({"'#새벽', 새벽", "'  새벽  ', 새벽", "'새 벽', 새벽", "'Dawn', dawn", "'#Rainy Day', rainyday"})
    @DisplayName("불변식 5 — 무드 이름은 정규화되어 같은 태그로 취급된다")
    void moodNameNormalization(String raw, String expected) {
        assertThat(Mood.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("무드 교체 시 사용량 카운터가 정확히 증감한다 (랭킹 정확도)")
    void moodUsageCounterTracksReplacement() {
        Record record = newRecord(RecordStatus.DONE);
        Mood dawn = Mood.ofFreeform("새벽");
        Mood rainy = Mood.ofFreeform("비오는날");

        record.replaceMoods(new java.util.LinkedHashSet<>(List.of(dawn, rainy)));
        assertThat(dawn.getUsageCount()).isEqualTo(1);
        assertThat(rainy.getUsageCount()).isEqualTo(1);

        // 새벽은 유지, 비오는날은 제거 → 유지된 무드는 중복 증가하지 않아야 한다
        record.replaceMoods(new java.util.LinkedHashSet<>(List.of(dawn)));
        assertThat(dawn.getUsageCount()).isEqualTo(1);
        assertThat(rainy.getUsageCount()).isZero();
    }

    @Test
    @DisplayName("삭제하면 붙어 있던 무드의 사용량이 회수된다")
    void softDeleteReleasesMoodUsage() {
        Record record = newRecord(RecordStatus.DONE);
        Mood dawn = Mood.ofFreeform("새벽");
        record.replaceMoods(new java.util.LinkedHashSet<>(List.of(dawn)));

        record.softDelete();

        assertThat(dawn.getUsageCount()).isZero();
        assertThat(record.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("공개 범위 — PRIVATE 은 본인에게만, FOLLOWERS 는 팔로워에게만 보인다")
    void visibilityRules() {
        Long owner = 1L;
        Long stranger = 2L;

        Record privateRecord = Record.create(owner, newContent(), RecordStatus.DONE, Visibility.PRIVATE);
        assertThat(privateRecord.isVisibleTo(owner, false)).isTrue();
        assertThat(privateRecord.isVisibleTo(stranger, true)).isFalse();

        Record followersRecord = Record.create(owner, newContent(), RecordStatus.DONE, Visibility.FOLLOWERS);
        assertThat(followersRecord.isVisibleTo(stranger, true)).isTrue();
        assertThat(followersRecord.isVisibleTo(stranger, false)).isFalse();
        assertThat(followersRecord.isVisibleTo(null, false)).isFalse();

        Record publicRecord = Record.create(owner, newContent(), RecordStatus.DONE, Visibility.PUBLIC);
        assertThat(publicRecord.isVisibleTo(null, false)).isTrue();
    }

    private Record newRecord(RecordStatus status) {
        return Record.create(1L, newContent(), status, Visibility.PUBLIC);
    }

    private Content newContent() {
        return Content.from(new ContentSnapshot(
                ContentType.BOOK,
                ProviderType.NAVER_BOOK,
                "9788937473135",
                "노르웨이의 숲",
                List.of("무라카미 하루키"),
                null,
                null,
                null,
                Map.of()));
    }
}
