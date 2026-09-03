package com.litmood.domain.model;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 기록 — 이 서비스의 핵심 엔티티 (F-03).
 *
 * 별점이 아니라 <b>무드</b>가 차별점이므로, 필수 입력은 {@code status} 하나뿐이다.
 * 별점도 리뷰도 없이 "새벽에 봤다"만 남기는 기록이 정상 상태다 (F-03-01 수용 기준).
 */
@Entity
@Table(name = "records")
public class Record extends BaseTimeEntity {

    /** 기록당 무드 상한 (F-03-04). */
    public static final int MAX_MOODS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordStatus status;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(length = 2000)
    private String review;

    @Column(name = "is_spoiler", nullable = false)
    private boolean spoiler = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    @Column(name = "context_note", length = 200)
    private String contextNote;

    @Column(name = "started_at")
    private LocalDate startedAt;

    @Column(name = "finished_at")
    private LocalDate finishedAt;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "record_moods",
            joinColumns = @JoinColumn(name = "record_id"),
            inverseJoinColumns = @JoinColumn(name = "mood_id"))
    private Set<Mood> moods = new LinkedHashSet<>();

    protected Record() {}

    public static Record create(
            Long userId, Content content, RecordStatus status, Visibility visibility) {
        Record record = new Record();
        record.userId = userId;
        record.content = content;
        record.status = status;
        record.visibility = visibility;
        return record;
    }

    /**
     * 별점 설정. 불변식 2 — 아직 보지 않은 콘텐츠에는 별점을 남길 수 없다.
     * DB 제약(ck_records_want_no_rating)도 같은 규칙을 걸어두었지만,
     * 여기서 먼저 막아야 사용자에게 의미 있는 메시지를 줄 수 있다.
     */
    public void changeRating(BigDecimal rating) {
        if (rating != null && !status.allowsRating()) {
            throw new LitmoodException(ErrorCode.RATING_NOT_ALLOWED);
        }
        this.rating = rating;
    }

    public void changeStatus(RecordStatus status) {
        this.status = status;
        // WANT 로 되돌리면 별점은 함께 사라져야 불변식이 유지된다
        if (!status.allowsRating()) {
            this.rating = null;
        }
    }

    public void replaceMoods(Set<Mood> newMoods) {
        if (newMoods.size() > MAX_MOODS) {
            throw new LitmoodException(ErrorCode.MOOD_LIMIT_EXCEEDED);
        }
        // 사용량 카운터는 랭킹(F-07)에 쓰이므로 교체 시 정확히 증감시킨다
        this.moods.stream().filter(m -> !newMoods.contains(m)).forEach(Mood::decreaseUsage);
        newMoods.stream().filter(m -> !this.moods.contains(m)).forEach(Mood::increaseUsage);

        this.moods.clear();
        this.moods.addAll(newMoods);
    }

    public void edit(
            String review, Boolean spoiler, Visibility visibility, String contextNote,
            LocalDate startedAt, LocalDate finishedAt, Integer repeatCount) {
        this.review = review;
        if (spoiler != null) {
            this.spoiler = spoiler;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
        this.contextNote = contextNote;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        if (repeatCount != null && repeatCount >= 0) {
            this.repeatCount = repeatCount;
        }
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.moods.forEach(Mood::decreaseUsage);
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    /** 조회자가 이 기록을 볼 수 있는가 (F-03-07). */
    public boolean isVisibleTo(Long viewerId, boolean viewerFollowsOwner) {
        if (isOwnedBy(viewerId)) {
            return true;
        }
        return switch (visibility) {
            case PUBLIC -> true;
            case FOLLOWERS -> viewerId != null && viewerFollowsOwner;
            case PRIVATE -> false;
        };
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Content getContent() {
        return content;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getReview() {
        return review;
    }

    public boolean isSpoiler() {
        return spoiler;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getContextNote() {
        return contextNote;
    }

    public LocalDate getStartedAt() {
        return startedAt;
    }

    public LocalDate getFinishedAt() {
        return finishedAt;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public Set<Mood> getMoods() {
        return moods;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
