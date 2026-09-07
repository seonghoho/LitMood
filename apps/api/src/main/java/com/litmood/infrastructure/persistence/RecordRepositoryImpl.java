package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Mood;
import com.litmood.domain.model.Record;
import com.litmood.domain.repository.RecordQuery;
import com.litmood.domain.repository.RecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

interface RecordJpaRepository extends JpaRepository<Record, Long> {

    @EntityGraph(attributePaths = {"content", "moods", "author"})
    @Query("SELECT r FROM Record r WHERE r.id = :id AND r.deletedAt IS NULL")
    Optional<Record> findActiveById(@Param("id") Long id);

    @Query("SELECT r FROM Record r WHERE r.userId = :userId AND r.content.id = :contentId AND r.deletedAt IS NULL")
    Optional<Record> findActiveByUserAndContent(@Param("userId") Long userId, @Param("contentId") Long contentId);

    @EntityGraph(attributePaths = {"content", "moods", "author"})
    @Query("SELECT r FROM Record r WHERE r.userId = :userId AND r.content.id IN :contentIds AND r.deletedAt IS NULL")
    List<Record> findActiveByUserAndContents(
            @Param("userId") Long userId, @Param("contentIds") List<Long> contentIds);

    @EntityGraph(attributePaths = {"content", "moods", "author"})
    @Query("SELECT r FROM Record r WHERE r.id IN :ids ORDER BY r.createdAt DESC, r.id DESC")
    List<Record> findAllWithDetails(@Param("ids") List<Long> ids);
}

/**
 * 타임라인은 조건 조합이 많아(F-04-02) Criteria API 로 동적 질의를 만든다.
 * 문자열 JPQL 조립보다 안전하고, 필터가 늘어나도 분기만 추가하면 된다.
 */
@Repository
class RecordRepositoryImpl implements RecordRepository {

    private final RecordJpaRepository jpa;
    private final EntityManager em;

    RecordRepositoryImpl(RecordJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public Record save(Record record) {
        return jpa.save(record);
    }

    @Override
    public Optional<Record> findActiveById(Long id) {
        return jpa.findActiveById(id);
    }

    @Override
    public Optional<Record> findActiveByUserAndContent(Long userId, Long contentId) {
        return jpa.findActiveByUserAndContent(userId, contentId);
    }

    @Override
    public List<Record> findActiveByUserAndContents(Long userId, List<Long> contentIds) {
        // IN () 은 SQL 문법 오류다. 빈 목록은 질의 없이 끝낸다.
        return contentIds.isEmpty() ? List.of() : jpa.findActiveByUserAndContents(userId, contentIds);
    }

    @Override
    public List<Record> findAllByIds(List<Long> ids) {
        // IN () 은 SQL 문법 오류다. 빈 목록은 질의 없이 끝낸다.
        return ids.isEmpty() ? List.of() : jpa.findAllWithDetails(ids);
    }

    @Override
    public List<Record> findTimeline(RecordQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = cb.createQuery(Long.class);
        Root<Record> root = criteria.from(Record.class);

        // 1단계: 조건에 맞는 id 만 정렬해 가져온다.
        //   엔티티를 바로 페치하면 컬렉션 조인 때문에 limit 이 어긋나므로,
        //   id 를 먼저 확정한 뒤 2단계에서 연관을 채운다.
        criteria.select(root.get("id"))
                .where(cb.and(predicates(cb, criteria, root, query).toArray(Predicate[]::new)))
                .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

        List<Long> ids = em.createQuery(criteria)
                .setMaxResults(query.limit())
                .getResultList();

        if (ids.isEmpty()) {
            return List.of();
        }
        // 2단계: EntityGraph 로 content·moods 를 한 번에 로드해 N+1 을 막는다
        return jpa.findAllWithDetails(ids);
    }

    @Override
    public long countTimeline(RecordQuery query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> criteria = cb.createQuery(Long.class);
        Root<Record> root = criteria.from(Record.class);

        criteria.select(cb.count(root))
                .where(cb.and(predicatesWithoutCursor(cb, criteria, root, query).toArray(Predicate[]::new)));

        return em.createQuery(criteria).getSingleResult();
    }

    private List<Predicate> predicates(
            CriteriaBuilder cb, CriteriaQuery<?> criteria, Root<Record> root, RecordQuery q) {
        List<Predicate> predicates = predicatesWithoutCursor(cb, criteria, root, q);

        // 커서: (createdAt, id) 의 사전식 비교.
        //   offset 을 쓰면 조회 사이에 기록이 추가될 때 항목이 중복/누락된다.
        if (q.cursorCreatedAt() != null && q.cursorId() != null) {
            predicates.add(cb.or(
                    cb.lessThan(root.get("createdAt"), q.cursorCreatedAt()),
                    cb.and(
                            cb.equal(root.get("createdAt"), q.cursorCreatedAt()),
                            cb.lessThan(root.get("id"), q.cursorId()))));
        }
        return predicates;
    }

    private List<Predicate> predicatesWithoutCursor(
            CriteriaBuilder cb, CriteriaQuery<?> criteria, Root<Record> root, RecordQuery q) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isNull(root.get("deletedAt")));

        // 피드는 팔로잉 여러 명을 한 번에 훑는다. 대상이 비면 결과도 비어야 하므로
        // "항상 거짓" 조건을 넣는다 — IN () 은 SQL 문법 오류가 된다.
        if (q.ownerIds() == null || q.ownerIds().isEmpty()) {
            predicates.add(cb.disjunction());
            return predicates;
        }
        predicates.add(root.get("userId").in(q.ownerIds()));

        // 차단은 양방향으로 가린다 (F-06-05)
        if (q.excludedUserIds() != null && !q.excludedUserIds().isEmpty()) {
            predicates.add(cb.not(root.get("userId").in(q.excludedUserIds())));
        }

        if (q.visibleTo() != null && !q.visibleTo().isEmpty()) {
            predicates.add(root.get("visibility").in(q.visibleTo()));
        }
        if (isNotEmpty(q.types())) {
            predicates.add(root.get("content").get("type").in(q.types()));
        }
        if (isNotEmpty(q.statuses())) {
            predicates.add(root.get("status").in(q.statuses()));
        }
        if (isNotEmpty(q.moodNames())) {
            // 무드를 JOIN 하면 기록 하나가 무드 수만큼 중복되어 DISTINCT 가 필요해지고,
            // Postgres 에서는 "SELECT DISTINCT 는 ORDER BY 표현식을 select 목록에 포함해야 한다"는
            // 제약에 걸린다. EXISTS 서브쿼리는 행을 늘리지 않아 이 문제가 아예 생기지 않는다.
            // 여러 무드를 지정하면 "그중 하나라도" 로 본다 — 필터 칩의 통상적 동작.
            Subquery<Long> hasMood = criteria.subquery(Long.class);
            Root<Record> subRoot = hasMood.from(Record.class);
            var moodJoin = subRoot.<Record, Mood>join("moods");
            hasMood.select(subRoot.get("id"))
                    .where(cb.equal(subRoot.get("id"), root.get("id")), moodJoin.get("name").in(q.moodNames()));
            predicates.add(cb.exists(hasMood));
        }
        if (q.minRating() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), q.minRating()));
        }
        if (q.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"), q.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (q.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(
                    root.get("createdAt"), q.to().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC)));
        }
        return predicates;
    }

    private static boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
