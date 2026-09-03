package com.litmood.domain.model;

/**
 * 외부 콘텐츠 provider (ADR-010).
 * 각 provider 는 정확히 하나의 {@link ContentType} 을 담당한다.
 */
public enum ProviderType {
    NAVER_BOOK(ContentType.BOOK),
    TMDB(ContentType.MOVIE),
    SPOTIFY(ContentType.MUSIC);

    private final ContentType contentType;

    ProviderType(ContentType contentType) {
        this.contentType = contentType;
    }

    public ContentType contentType() {
        return contentType;
    }
}
