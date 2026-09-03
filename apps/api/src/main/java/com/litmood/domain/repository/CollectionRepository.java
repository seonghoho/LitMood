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

    boolean existsBySlug(String slug);
}
