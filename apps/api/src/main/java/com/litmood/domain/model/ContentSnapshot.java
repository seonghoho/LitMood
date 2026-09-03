package com.litmood.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 외부 provider 응답을 정규화한 공통 콘텐츠 모델 (ADR-010).
 *
 * 프론트엔드는 이 형태만 본다 — provider 가 무엇인지, 어떤 필드명을 쓰는지 몰라도 된다.
 * provider 를 교체하거나 추가해도 이 모델이 방파제 역할을 한다.
 */
public record ContentSnapshot(
        ContentType type,
        ProviderType provider,
        String externalId,
        String title,
        List<String> creators,
        LocalDate releasedOn,
        String coverUrl,
        String description,
        Map<String, Object> metadata) {

    public ContentSnapshot {
        creators = creators == null ? List.of() : List.copyOf(creators);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
