package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Collection;
import com.litmood.domain.model.LikeTarget;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.Report;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.CollectionRepository;
import com.litmood.domain.repository.RecordRepository;
import com.litmood.domain.repository.ReportRepository;
import com.litmood.domain.repository.SocialRepository;
import com.litmood.domain.repository.UserRepository;
import com.litmood.interfaces.dto.SocialDtos.BlockedUserResponse;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-06 — 팔로우, 좋아요, 차단, 신고. */
@Service
public class SocialService {

    /** 차단 목록은 화면 하나에 담기는 크기면 충분하다. 더 필요해지면 커서 페이징을 붙인다. */
    private static final int MAX_BLOCK_LIST = 100;

    private final SocialRepository socialRepository;
    private final UserRepository userRepository;
    private final RecordRepository recordRepository;
    private final CollectionRepository collectionRepository;
    private final ReportRepository reportRepository;

    public SocialService(
            SocialRepository socialRepository,
            UserRepository userRepository,
            RecordRepository recordRepository,
            CollectionRepository collectionRepository,
            ReportRepository reportRepository) {
        this.socialRepository = socialRepository;
        this.userRepository = userRepository;
        this.recordRepository = recordRepository;
        this.collectionRepository = collectionRepository;
        this.reportRepository = reportRepository;
    }

    // ── 팔로우 ──────────────────────────────────────────────

    @Transactional
    public void follow(Long followerId, String targetHandle) {
        User target = requireUser(targetHandle);

        if (target.getId().equals(followerId)) {
            throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "자기 자신은 팔로우할 수 없습니다");
        }
        if (socialRepository.isBlockedBetween(followerId, target.getId())) {
            // 차단 관계를 그대로 알려주면 차단 사실이 노출된다. 일반적인 거부로 응답한다.
            throw new LitmoodException(ErrorCode.FORBIDDEN, "팔로우할 수 없는 사용자입니다");
        }
        socialRepository.follow(followerId, target.getId());
    }

    @Transactional
    public void unfollow(Long followerId, String targetHandle) {
        socialRepository.unfollow(followerId, requireUser(targetHandle).getId());
    }

    @Transactional(readOnly = true)
    public FollowStats stats(Long viewerId, User target) {
        return new FollowStats(
                socialRepository.countFollowers(target.getId()),
                socialRepository.countFollowing(target.getId()),
                socialRepository.isFollowing(viewerId, target.getId()),
                // 가림은 양방향이지만 버튼 상태는 내가 건 차단만 따진다
                socialRepository.isBlocking(viewerId, target.getId()));
    }

    /** 내가 차단한 사용자 목록 (F-06-05). 풀 수 있는 곳이 없으면 차단은 되돌릴 수 없는 동작이 된다. */
    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocked(Long userId) {
        return socialRepository.findBlockedUsers(userId, MAX_BLOCK_LIST).stream()
                .map(blocked -> new BlockedUserResponse(
                        blocked.user().getHandle(),
                        blocked.user().getNickname(),
                        blocked.user().getAvatarUrl(),
                        blocked.blockedAt()))
                .toList();
    }

    // ── 좋아요 ──────────────────────────────────────────────

    @Transactional
    public int likeRecord(Long userId, Long recordId) {
        Record record = requireVisibleRecord(userId, recordId);
        if (socialRepository.like(userId, LikeTarget.RECORD, recordId)) {
            record.increaseLikeCount();
        }
        return record.getLikeCount();
    }

    @Transactional
    public int unlikeRecord(Long userId, Long recordId) {
        Record record = recordRepository
                .findActiveById(recordId)
                .orElseThrow(() -> LitmoodException.notFound("기록"));
        if (socialRepository.unlike(userId, LikeTarget.RECORD, recordId)) {
            record.decreaseLikeCount();
        }
        return record.getLikeCount();
    }

    @Transactional
    public int likeCollection(Long userId, String slug) {
        Collection collection = requireVisibleCollection(userId, slug);
        if (socialRepository.like(userId, LikeTarget.COLLECTION, collection.getId())) {
            collection.increaseLikeCount();
        }
        return collection.getLikeCount();
    }

    @Transactional
    public int unlikeCollection(Long userId, String slug) {
        Collection collection = collectionRepository
                .findActiveBySlug(slug)
                .orElseThrow(() -> LitmoodException.notFound("컬렉션"));
        if (socialRepository.unlike(userId, LikeTarget.COLLECTION, collection.getId())) {
            collection.decreaseLikeCount();
        }
        return collection.getLikeCount();
    }

    // ── 차단 ────────────────────────────────────────────────

    @Transactional
    public void block(Long blockerId, String targetHandle) {
        User target = requireUser(targetHandle);
        if (target.getId().equals(blockerId)) {
            throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "자기 자신은 차단할 수 없습니다");
        }
        socialRepository.block(blockerId, target.getId());
    }

    @Transactional
    public void unblock(Long blockerId, String targetHandle) {
        socialRepository.unblock(blockerId, requireUser(targetHandle).getId());
    }

    // ── 신고 ────────────────────────────────────────────────

    @Transactional
    public void report(
            Long reporterId,
            Report.ReportTarget targetType,
            Long targetId,
            Report.ReportReason reason,
            String detail) {

        if (reportRepository.existsBy(reporterId, targetType, targetId)) {
            // 반복 신고로 처리 큐를 채우는 것을 막는다. 사용자에겐 성공으로 보인다.
            return;
        }
        reportRepository.save(Report.of(reporterId, targetType, targetId, reason, detail));
    }

    // ── 조회 보조 ───────────────────────────────────────────

    /** 차단으로 가려야 할 사용자 집합. 피드·목록 조회에서 제외 조건으로 쓴다. */
    @Transactional(readOnly = true)
    public Set<Long> hiddenUserIds(Long viewerId) {
        return socialRepository.findHiddenUserIds(viewerId);
    }

    private User requireUser(String handle) {
        return userRepository.findActiveByHandle(handle).orElseThrow(() -> LitmoodException.notFound("사용자"));
    }

    private Record requireVisibleRecord(Long viewerId, Long recordId) {
        Record record = recordRepository
                .findActiveById(recordId)
                .orElseThrow(() -> LitmoodException.notFound("기록"));

        boolean follows = socialRepository.isFollowing(viewerId, record.getUserId());
        if (!record.isVisibleTo(viewerId, follows)
                || socialRepository.isBlockedBetween(viewerId, record.getUserId())) {
            throw LitmoodException.notFound("기록");
        }
        return record;
    }

    private Collection requireVisibleCollection(Long viewerId, String slug) {
        Collection collection = collectionRepository
                .findActiveBySlug(slug)
                .orElseThrow(() -> LitmoodException.notFound("컬렉션"));

        boolean follows = socialRepository.isFollowing(viewerId, collection.getUserId());
        if (!collection.isVisibleTo(viewerId, follows)
                || socialRepository.isBlockedBetween(viewerId, collection.getUserId())) {
            throw LitmoodException.notFound("컬렉션");
        }
        return collection;
    }

    public record FollowStats(long followers, long following, boolean followedByMe, boolean blockedByMe) {}
}
