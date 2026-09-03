package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Content;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.repository.ContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콘텐츠 스냅샷 확보 (F-02-05).
 *
 * 기록과 컬렉션이 모두 필요로 하는 동작이라 별도 서비스로 둔다.
 * 컬렉션은 기록과 독립적이므로(F-05 설계 원칙), 기록하지 않은 콘텐츠도
 * 이 경로를 통해 자체 DB 에 스냅샷을 남긴 뒤 담을 수 있다.
 */
@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final ContentSearchService contentSearchService;

    public ContentService(ContentRepository contentRepository, ContentSearchService contentSearchService) {
        this.contentRepository = contentRepository;
        this.contentSearchService = contentSearchService;
    }

    /** 이미 있으면 재사용하고, 없으면 provider 에서 가져와 저장한다. */
    @Transactional
    public Content resolveOrCreate(ProviderType provider, String externalId) {
        return contentRepository
                .findByProviderAndExternalId(provider, externalId)
                .orElseGet(() -> {
                    ContentSnapshot snapshot = contentSearchService
                            .findByExternalId(provider, externalId)
                            .orElseThrow(() -> new LitmoodException(
                                    ErrorCode.PROVIDER_UNAVAILABLE, "콘텐츠 정보를 가져오지 못했습니다"));
                    return contentRepository.save(Content.from(snapshot));
                });
    }
}
