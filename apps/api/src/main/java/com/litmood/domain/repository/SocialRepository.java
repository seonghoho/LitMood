package com.litmood.domain.repository;

import com.litmood.domain.model.LikeTarget;
import com.litmood.domain.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 관계(팔로우·차단·좋아요) 조회·변경 (F-06).
 *
 * 세 관계는 거의 항상 함께 쓰인다 — 피드 하나를 그리려면 팔로잉 목록,
 * 차단 목록, 내가 누른 좋아요를 모두 알아야 한다. 하나의 포트로 묶는다.
 */
public interface SocialRepository {

    // ── 팔로우 ──────────────────────────────────────────────
    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    boolean isFollowing(Long followerId, Long followeeId);

    /** 피드 조회용 — 내가 팔로우한 사용자 id 들. */
    List<Long> findFolloweeIds(Long followerId);

    long countFollowers(Long userId);

    long countFollowing(Long userId);

    // ── 차단 ────────────────────────────────────────────────
    void block(Long blockerId, Long blockedId);

    void unblock(Long blockerId, Long blockedId);

    /** 내가 차단했거나 나를 차단한 사용자 — 양방향으로 가린다. */
    Set<Long> findHiddenUserIds(Long userId);

    boolean isBlockedBetween(Long a, Long b);

    /**
     * 내가 상대를 차단했는지 — <b>단방향</b>이다.
     *
     * <p>가림 판정은 양방향이지만(그래야 차단이 목적을 달성한다), 화면의 버튼을
     * "차단"으로 그릴지 "차단 해제"로 그릴지는 내가 건 차단만 따져야 한다.
     * 상대가 나를 차단한 것을 내가 풀 수는 없다.
     */
    boolean isBlocking(Long blockerId, Long blockedId);

    /** 내가 차단한 사용자 — 최근 차단한 순 (F-06-05 차단 목록). */
    List<BlockedUser> findBlockedUsers(Long blockerId, int limit);

    record BlockedUser(User user, Instant blockedAt) {}

    // ── 좋아요 ──────────────────────────────────────────────
    boolean like(Long userId, LikeTarget targetType, Long targetId);

    boolean unlike(Long userId, LikeTarget targetType, Long targetId);

    /** 목록 화면에서 "내가 누른 것"을 한 번에 판정한다 (N+1 방지). */
    Set<Long> findLikedTargetIds(Long userId, LikeTarget targetType, List<Long> targetIds);
}
