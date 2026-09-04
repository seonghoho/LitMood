package com.litmood.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/** 무드 태그 (F-03-04). 이 서비스의 1급 개념이다. */
@Entity
@Table(name = "moods")
public class Mood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 정규화된 이름 — 중복 판정의 기준 (불변식 5). */
    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    /** 큐레이션 무드만 고유 색을 갖는다. 자유 입력 무드는 null (중립색으로 표시). */
    @Column(length = 7)
    private String color;

    @Column(name = "is_curated", nullable = false)
    private boolean curated = false;

    @Column(name = "usage_count", nullable = false)
    private long usageCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Mood() {}

    /** 사용자가 자유 입력한 무드. */
    public static Mood ofFreeform(String rawName) {
        Mood mood = new Mood();
        mood.name = normalize(rawName);
        mood.displayName = rawName.trim();
        return mood;
    }

    /**
     * 무드 이름 정규화 (불변식 5).
     * "#새벽", "새벽 ", "새벽" 은 모두 같은 태그여야 한다.
     * 프론트의 normalizeMoodName() 과 동일한 규칙을 유지해야 한다.
     */
    public static String normalize(String raw) {
        return raw.trim().replaceFirst("^#", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public void increaseUsage() {
        this.usageCount++;
    }

    public void decreaseUsage() {
        if (this.usageCount > 0) {
            this.usageCount--;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public boolean isCurated() {
        return curated;
    }

    public long getUsageCount() {
        return usageCount;
    }
}
