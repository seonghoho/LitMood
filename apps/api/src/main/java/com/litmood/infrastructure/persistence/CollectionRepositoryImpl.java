package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Collection;
import com.litmood.domain.model.Visibility;
import com.litmood.domain.repository.CollectionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

interface CollectionJpaRepository extends JpaRepository<Collection, Long> {

    @Query("SELECT c FROM Collection c WHERE c.slug = :slug AND c.deletedAt IS NULL")
    Optional<Collection> findActiveBySlug(@Param("slug") String slug);

    // 아이템과 콘텐츠를 함께 가져와 N+1 을 막는다
    @EntityGraph(attributePaths = {"items", "items.content"})
    @Query("SELECT c FROM Collection c WHERE c.slug = :slug AND c.deletedAt IS NULL")
    Optional<Collection> findActiveBySlugWithItems(@Param("slug") String slug);

    @EntityGraph(attributePaths = {"items", "items.content"})
    @Query("""
            SELECT c FROM Collection c
            WHERE c.userId = :userId AND c.deletedAt IS NULL AND c.visibility IN :visibleTo
            ORDER BY c.createdAt DESC
            """)
    List<Collection> findByOwner(
            @Param("userId") Long userId, @Param("visibleTo") List<Visibility> visibleTo, Limit limit);

    boolean existsBySlug(String slug);
}

@Repository
class CollectionRepositoryImpl implements CollectionRepository {

    private final CollectionJpaRepository jpa;

    CollectionRepositoryImpl(CollectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Collection save(Collection collection) {
        return jpa.save(collection);
    }

    @Override
    public Optional<Collection> findActiveBySlug(String slug) {
        return jpa.findActiveBySlug(slug);
    }

    @Override
    public Optional<Collection> findActiveBySlugWithItems(String slug) {
        return jpa.findActiveBySlugWithItems(slug);
    }

    @Override
    public List<Collection> findByOwner(Long userId, List<Visibility> visibleTo, int limit) {
        return jpa.findByOwner(userId, visibleTo, Limit.of(limit));
    }

    @Override
    public List<Collection> findAllByIds(List<Long> ids) {
        // IN () 은 SQL 문법 오류다. 빈 목록은 질의 없이 끝낸다.
        return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }
}
