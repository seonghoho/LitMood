package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 외부 콘텐츠의 자체 스냅샷 (F-02-05).
 *
 * 외부 API 는 언제든 응답이 바뀌거나 항목이 사라진다. 누군가 기록한 콘텐츠는
 * 반드시 복사본을 남겨야 provider 가 죽어도 기록이 살아남는다 (NFR-03).
 * 콘텐츠는 삭제되지 않는다 — 기록이 0개가 되어도 재기록 시 재활용된다 (불변식 4).
 */
@Entity
@Table(name = "contents")
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderType provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    // 타입별로 의미가 다르다: 저자 / 감독 / 아티스트
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]", nullable = false)
    private String[] creators = new String[0];

    @Column(name = "released_on")
    private LocalDate releasedOn;

    @Column(name = "cover_url", columnDefinition = "text")
    private String coverUrl;

    @Column(columnDefinition = "text")
    private String description;

    /** 타입별 고유 필드 — 콘텐츠 종류를 추가해도 스키마 변경이 필요 없다 (ADR-004). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = Map.of();

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Content() {}

    public static Content from(ContentSnapshot snapshot) {
        Content content = new Content();
        content.applySnapshot(snapshot);
        return content;
    }

    /** 외부 메타데이터가 갱신됐을 때 스냅샷을 덮어쓴다. id 와 생성 시각은 유지된다. */
    public void applySnapshot(ContentSnapshot snapshot) {
        this.type = snapshot.type();
        this.provider = snapshot.provider();
        this.externalId = snapshot.externalId();
        this.title = snapshot.title();
        this.creators = snapshot.creators().toArray(String[]::new);
        this.releasedOn = snapshot.releasedOn();
        this.coverUrl = snapshot.coverUrl();
        this.description = snapshot.description();
        this.metadata = snapshot.metadata();
        this.syncedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ContentType getType() {
        return type;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getCreators() {
        return creators == null ? List.of() : Arrays.asList(creators);
    }

    public LocalDate getReleasedOn() {
        return releasedOn;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getMetadata() {
        return metadata == null ? Map.of() : metadata;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
