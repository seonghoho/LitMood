package com.litmood.domain.model;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 컬렉션 — 여러 콘텐츠를 하나의 정서로 묶은 큐레이션 (F-05).
 *
 * 기록과 <b>독립적</b>이다. 기록하지 않은 콘텐츠도 담을 수 있다.
 * 컬렉션 공유가 이 서비스의 주된 유입 경로이므로 slug 로 공개 URL 을 갖는다.
 */
@Entity
@Table(name = "collections")
public class Collection extends BaseTimeEntity {

    /** 한 컬렉션에 담을 수 있는 최대 아이템 수. OG 이미지·페이지 렌더 비용의 상한이기도 하다. */
    public static final int MAX_ITEMS = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "cover_url")
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility = Visibility.PUBLIC;

    /** 비정규화 카운터 — 목록 화면에서 아이템을 로드하지 않고 개수를 보여주기 위함. */
    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "collection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<CollectionItem> items = new ArrayList<>();

    protected Collection() {}

    public static Collection create(Long userId, String slug, String title, String description, Visibility visibility) {
        Collection collection = new Collection();
        collection.userId = userId;
        collection.slug = slug;
        collection.title = title;
        collection.description = description;
        collection.visibility = visibility == null ? Visibility.PUBLIC : visibility;
        return collection;
    }

    /**
     * 공유 URL 이 되는 slug 를 만든다.
     *
     * 제목을 그대로 쓰면 중복되고, 랜덤 문자열만 쓰면 링크가 읽히지 않는다.
     * "제목-랜덤6자" 조합으로 가독성과 유일성을 함께 얻는다 — 재시도 루프가 필요 없다.
     * 한글은 URL 에 그대로 쓸 수 있으므로 로마자 변환은 하지 않는다.
     */
    public static String generateSlug(String title) {
        String base = title.trim()
                .toLowerCase(Locale.KOREAN)
                .replaceAll("[^\\p{IsHangul}\\p{IsAlphabetic}\\p{IsDigit}\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");

        if (base.isBlank()) {
            base = "collection";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60).replaceAll("-$", "");
        }
        return base + "-" + randomSuffix();
    }

    private static String randomSuffix() {
        return Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36), 36);
    }

    /**
     * 부분 수정 — <b>넣지 않은 필드(null)는 변경되지 않는다.</b> 지우려면 빈 문자열을 보낸다.
     *
     * <p>기록의 규칙과 같다. 예전에는 description·coverUrl 을 그대로 대입해
     * 제목만 고치는 요청이 설명과 커버를 함께 날렸다.
     */
    public void edit(String title, String description, String coverUrl, Visibility visibility) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description;
        }
        if (coverUrl != null) {
            // 지우면 다시 첫 아이템의 표지를 따라간다 (resolveCoverUrl)
            this.coverUrl = coverUrl.isBlank() ? null : coverUrl;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
    }

    public void addItem(Content content, String note) {
        if (items.size() >= MAX_ITEMS) {
            throw new LitmoodException(
                    ErrorCode.VALIDATION_FAILED, "컬렉션에는 최대 %d개까지 담을 수 있습니다".formatted(MAX_ITEMS));
        }
        boolean duplicate = items.stream().anyMatch(item -> item.getContent().getId().equals(content.getId()));
        if (duplicate) {
            throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "이미 담긴 콘텐츠입니다");
        }

        int nextPosition = items.stream().mapToInt(CollectionItem::getPosition).max().orElse(-1) + 1;
        items.add(CollectionItem.of(this, content, nextPosition, note));
        itemCount = items.size();
    }

    public void removeItem(Long contentId) {
        boolean removed = items.removeIf(item -> item.getContent().getId().equals(contentId));
        if (!removed) {
            throw LitmoodException.notFound("컬렉션 아이템");
        }
        // 제거 후 position 을 다시 촘촘하게 만든다 — 구멍이 남으면 정렬 변경이 꼬인다
        resequence();
        itemCount = items.size();
    }

    /**
     * 담은 이유를 나중에 고친다 (F-05-03).
     *
     * <p>빈 문자열이면 지운다 — 기록의 규칙과 같다. 담기지 않은 콘텐츠를 가리키면
     * 404 다: 없는 아이템의 노트를 조용히 만들어 두면 목록에 나타나지 않는 값이 남는다.
     */
    public void changeItemNote(Long contentId, String note) {
        CollectionItem item = items.stream()
                .filter(candidate -> candidate.getContent().getId().equals(contentId))
                .findFirst()
                .orElseThrow(() -> LitmoodException.notFound("컬렉션 아이템"));
        item.changeNote(note == null || note.isBlank() ? null : note);
    }

    /** 큐레이션에서 순서는 의미다. 전달된 순서대로 position 을 다시 매긴다. */
    public void reorder(List<Long> contentIdsInOrder) {
        Map<Long, Integer> desired = new java.util.HashMap<>();
        for (int i = 0; i < contentIdsInOrder.size(); i++) {
            desired.put(contentIdsInOrder.get(i), i);
        }
        // 목록에 없는 아이템은 뒤로 밀되 기존 상대 순서를 유지한다
        int tail = contentIdsInOrder.size();
        for (CollectionItem item : items) {
            Integer position = desired.get(item.getContent().getId());
            item.moveTo(position != null ? position : tail++);
        }
        items.sort(Comparator.comparingInt(CollectionItem::getPosition));
    }

    private void resequence() {
        items.sort(Comparator.comparingInt(CollectionItem::getPosition));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).moveTo(i);
        }
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }

    public boolean isVisibleTo(Long viewerId, boolean viewerFollowsOwner) {
        if (viewerId != null && isOwnedBy(viewerId)) {
            return true;
        }
        return switch (visibility) {
            case PUBLIC -> true;
            case FOLLOWERS -> viewerId != null && viewerFollowsOwner;
            case PRIVATE -> false;
        };
    }

    /** 커버가 없으면 첫 아이템의 표지를 대표 이미지로 쓴다 (OG 이미지·목록 썸네일). */
    public String resolveCoverUrl() {
        if (coverUrl != null && !coverUrl.isBlank()) {
            return coverUrl;
        }
        return items.stream()
                .min(Comparator.comparingInt(CollectionItem::getPosition))
                .map(item -> item.getContent().getCoverUrl())
                .orElse(null);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public List<CollectionItem> getItems() {
        return items;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
