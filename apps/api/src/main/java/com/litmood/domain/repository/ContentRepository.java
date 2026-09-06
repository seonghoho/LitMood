package com.litmood.domain.repository;

import com.litmood.domain.model.Content;
import com.litmood.domain.model.ProviderType;
import java.util.List;
import java.util.Optional;

public interface ContentRepository {

    Content save(Content content);

    Optional<Content> findById(Long id);

    Optional<Content> findByProviderAndExternalId(ProviderType provider, String externalId);

    /**
     * 여러 개를 한 번에. 랭킹처럼 id 목록을 먼저 얻는 조회가 하나씩 돌면 N+1 이 된다.
     *
     * <p>반환 순서는 보장하지 않는다 — 순서에 의미가 있는 호출부가 스스로 다시 세운다.
     */
    List<Content> findAllById(List<Long> ids);
}
