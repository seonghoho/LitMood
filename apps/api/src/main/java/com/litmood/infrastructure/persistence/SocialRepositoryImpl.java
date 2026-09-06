package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.Block;
import com.litmood.domain.model.Follow;
import com.litmood.domain.model.Like;
import com.litmood.domain.model.LikeTarget;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.SocialRepository;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 관계 테이블은 복합 키의 존재 여부만 다루는 단순 연산이 대부분이라
 * JPA 리포지토리 추상화보다 EntityManager 직접 사용이 읽기 쉽다.
 */
@Repository
class SocialRepositoryImpl implements SocialRepository {

    private final EntityManager em;

    SocialRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public void follow(Long followerId, Long followeeId) {
        if (isFollowing(followerId, followeeId)) {
            return; // 멱등 — 이미 팔로우 중이면 조용히 성공
        }
        em.persist(Follow.of(followerId, followeeId));
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        em.createQuery("DELETE FROM Follow f WHERE f.id.followerId = :a AND f.id.followeeId = :b")
                .setParameter("a", followerId)
                .setParameter("b", followeeId)
                .executeUpdate();
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            return false;
        }
        return em.createQuery(
                        "SELECT count(f) FROM Follow f WHERE f.id.followerId = :a AND f.id.followeeId = :b",
                        Long.class)
                .setParameter("a", followerId)
                .setParameter("b", followeeId)
                .getSingleResult()
                > 0;
    }

    @Override
    public List<Long> findFolloweeIds(Long followerId) {
        return em.createQuery(
                        "SELECT f.id.followeeId FROM Follow f WHERE f.id.followerId = :a", Long.class)
                .setParameter("a", followerId)
                .getResultList();
    }

    @Override
    public long countFollowers(Long userId) {
        return em.createQuery("SELECT count(f) FROM Follow f WHERE f.id.followeeId = :u", Long.class)
                .setParameter("u", userId)
                .getSingleResult();
    }

    @Override
    public long countFollowing(Long userId) {
        return em.createQuery("SELECT count(f) FROM Follow f WHERE f.id.followerId = :u", Long.class)
                .setParameter("u", userId)
                .getSingleResult();
    }

    @Override
    public void block(Long blockerId, Long blockedId) {
        boolean exists = em.createQuery(
                        "SELECT count(b) FROM Block b WHERE b.id.blockerId = :a AND b.id.blockedId = :b",
                        Long.class)
                .setParameter("a", blockerId)
                .setParameter("b", blockedId)
                .getSingleResult()
                > 0;
        if (!exists) {
            em.persist(Block.of(blockerId, blockedId));
        }
        // 차단하면 기존 팔로우 관계는 양쪽 모두 끊는다.
        // 남겨두면 차단 해제 시 의도치 않게 관계가 되살아난다.
        unfollow(blockerId, blockedId);
        unfollow(blockedId, blockerId);
    }

    @Override
    public void unblock(Long blockerId, Long blockedId) {
        em.createQuery("DELETE FROM Block b WHERE b.id.blockerId = :a AND b.id.blockedId = :b")
                .setParameter("a", blockerId)
                .setParameter("b", blockedId)
                .executeUpdate();
    }

    @Override
    public Set<Long> findHiddenUserIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        // 내가 차단한 사람 + 나를 차단한 사람 (양방향 가림)
        List<Long> blocked = em.createQuery(
                        "SELECT b.id.blockedId FROM Block b WHERE b.id.blockerId = :u", Long.class)
                .setParameter("u", userId)
                .getResultList();
        List<Long> blockers = em.createQuery(
                        "SELECT b.id.blockerId FROM Block b WHERE b.id.blockedId = :u", Long.class)
                .setParameter("u", userId)
                .getResultList();

        Set<Long> hidden = new HashSet<>(blocked);
        hidden.addAll(blockers);
        return hidden;
    }

    @Override
    public boolean isBlocking(Long blockerId, Long blockedId) {
        if (blockerId == null || blockedId == null) {
            return false;
        }
        return em.createQuery(
                        "SELECT count(b) FROM Block b WHERE b.id.blockerId = :a AND b.id.blockedId = :b",
                        Long.class)
                .setParameter("a", blockerId)
                .setParameter("b", blockedId)
                .getSingleResult()
                > 0;
    }

    @Override
    public List<BlockedUser> findBlockedUsers(Long blockerId, int limit) {
        if (blockerId == null) {
            return List.of();
        }
        // 사용자를 한 번에 조인해 온다 — id 만 받아 와 하나씩 조회하면 N+1 이 된다.
        // 탈퇴한 사용자는 목록에서 뺀다(차단 관계 자체는 남겨 둔다 — 되살아나면 곤란하다).
        return em
                .createQuery(
                        """
                        SELECT u, b.createdAt FROM Block b
                        JOIN User u ON u.id = b.id.blockedId
                        WHERE b.id.blockerId = :blocker AND u.deletedAt IS NULL
                        ORDER BY b.createdAt DESC
                        """,
                        Object[].class)
                .setParameter("blocker", blockerId)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new BlockedUser((User) row[0], (Instant) row[1]))
                .toList();
    }

    @Override
    public boolean isBlockedBetween(Long a, Long b) {
        if (a == null || b == null) {
            return false;
        }
        return em.createQuery(
                        """
                        SELECT count(bl) FROM Block bl
                        WHERE (bl.id.blockerId = :a AND bl.id.blockedId = :b)
                           OR (bl.id.blockerId = :b AND bl.id.blockedId = :a)
                        """,
                        Long.class)
                .setParameter("a", a)
                .setParameter("b", b)
                .getSingleResult()
                > 0;
    }

    @Override
    public boolean like(Long userId, LikeTarget targetType, Long targetId) {
        if (isLiked(userId, targetType, targetId)) {
            return false; // 이미 눌렀다 — 카운터를 중복 증가시키지 않도록 알린다
        }
        em.persist(Like.of(userId, targetType, targetId));
        return true;
    }

    @Override
    public boolean unlike(Long userId, LikeTarget targetType, Long targetId) {
        int deleted = em.createQuery(
                        """
                        DELETE FROM Like l
                        WHERE l.id.userId = :u AND l.id.targetType = :t AND l.id.targetId = :id
                        """)
                .setParameter("u", userId)
                .setParameter("t", targetType)
                .setParameter("id", targetId)
                .executeUpdate();
        return deleted > 0;
    }

    @Override
    public Set<Long> findLikedTargetIds(Long userId, LikeTarget targetType, List<Long> targetIds) {
        if (userId == null || targetIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery(
                        """
                        SELECT l.id.targetId FROM Like l
                        WHERE l.id.userId = :u AND l.id.targetType = :t AND l.id.targetId IN :ids
                        """,
                        Long.class)
                .setParameter("u", userId)
                .setParameter("t", targetType)
                .setParameter("ids", targetIds)
                .getResultList());
    }

    private boolean isLiked(Long userId, LikeTarget targetType, Long targetId) {
        return em.createQuery(
                        """
                        SELECT count(l) FROM Like l
                        WHERE l.id.userId = :u AND l.id.targetType = :t AND l.id.targetId = :id
                        """,
                        Long.class)
                .setParameter("u", userId)
                .setParameter("t", targetType)
                .setParameter("id", targetId)
                .getSingleResult()
                > 0;
    }
}
