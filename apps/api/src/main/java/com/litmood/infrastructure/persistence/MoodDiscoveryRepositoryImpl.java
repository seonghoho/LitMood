package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Content;
import com.litmood.domain.repository.MoodDiscoveryRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class MoodDiscoveryRepositoryImpl implements MoodDiscoveryRepository {

    private final EntityManager em;

    MoodDiscoveryRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<ContentRanking> rankByMood(String normalizedMoodName, int limit) {
        // PRIVATE 기록은 집계에서 제외한다 (도메인 불변식 3).
        // FOLLOWERS 도 제외한다 — 탐색은 비로그인 사용자에게도 같은 결과를 보여야 한다.
        List<Object[]> rows = em.createQuery(
                        """
                        SELECT r.content, count(r), avg(r.rating)
                        FROM Record r JOIN r.moods m
                        WHERE m.name = :moodName
                          AND r.visibility = com.litmood.domain.model.Visibility.PUBLIC
                          AND r.deletedAt IS NULL
                        GROUP BY r.content
                        ORDER BY count(r) DESC, max(r.createdAt) DESC
                        """,
                        Object[].class)
                .setParameter("moodName", normalizedMoodName)
                .setMaxResults(limit)
                .getResultList();

        return rows.stream()
                .map(row -> new ContentRanking(
                        (Content) row[0],
                        (Long) row[1],
                        row[2] == null ? null : ((Number) row[2]).doubleValue()))
                .toList();
    }
}
