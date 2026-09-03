package com.litmood.application.port;

import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import java.util.List;
import java.util.Optional;

/**
 * 외부 콘텐츠 provider 추상화 (ADR-010).
 *
 * application 이 인터페이스를 소유하고 infrastructure 가 구현한다(의존성 역전).
 * 새로운 provider 를 붙이는 일은 구현체 하나를 추가하는 것으로 끝난다 —
 * 검색 유스케이스는 전혀 변경되지 않는다.
 */
public interface ContentProvider {

    ProviderType providerType();

    default ContentType contentType() {
        return providerType().contentType();
    }

    /** 키워드 검색. 실패 시 예외를 던지며, 부분 실패 처리는 호출부가 담당한다. */
    List<ContentSnapshot> search(String query, int limit);

    /** 상세 조회 — 기록 생성 시 콘텐츠 스냅샷을 만들기 위해 사용한다 (F-02-05). */
    Optional<ContentSnapshot> findByExternalId(String externalId);

    /** 필수 자격증명이 설정되어 있는지. 미설정 provider 는 검색에서 제외된다. */
    boolean isConfigured();
}
