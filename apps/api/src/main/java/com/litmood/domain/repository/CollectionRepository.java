package com.litmood.domain.repository;

import com.litmood.domain.model.Collection;
import java.util.List;
import java.util.Optional;

public interface CollectionRepository {

    Collection save(Collection collection);

    Optional<Collection> findActiveBySlug(String slug);

    /** 상세 화면용 — 아이템과 콘텐츠를 함께 로드한다. */
    Optional<Collection> findActiveBySlugWithItems(String slug);

    List<Collection> findByOwner(Long userId, List<com.litmood.domain.model.Visibility> visibleTo, int limit);

    /** 여러 컬렉션을 한 번에. 삭제된 것도 포함한다 (신고 큐 #28). */
    List<Collection> findAllByIds(List<Long> ids);

    boolean existsBySlug(String slug);
}
