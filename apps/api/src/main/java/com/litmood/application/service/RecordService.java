package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Content;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.LikeTarget;
import com.litmood.domain.model.Mood;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.User;
import com.litmood.domain.model.Visibility;
import com.litmood.domain.repository.MoodRepository;
import com.litmood.domain.repository.RecordQuery;
import com.litmood.domain.repository.RecordRepository;
import com.litmood.domain.repository.SocialRepository;
import com.litmood.domain.repository.UserRepository;
import com.litmood.infrastructure.redis.PopularityRanking;
import com.litmood.interfaces.dto.RecordDtos.CreateRecordRequest;
import com.litmood.interfaces.dto.RecordDtos.RecordPage;
import com.litmood.interfaces.dto.RecordDtos.RecordResponse;
import com.litmood.interfaces.dto.RecordDtos.UpdateRecordRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-03·F-04 — 기록 CRUD 와 타임라인. */
@Service
public class RecordService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final RecordRepository recordRepository;
    private final MoodRepository moodRepository;
    private final UserRepository userRepository;
    private final ContentService contentService;
    private final SocialRepository socialRepository;
    private final PopularityRanking popularityRanking;

    public RecordService(
            RecordRepository recordRepository,
            MoodRepository moodRepository,
            UserRepository userRepository,
            ContentService contentService,
            SocialRepository socialRepository,
            PopularityRanking popularityRanking) {
        this.recordRepository = recordRepository;
        this.moodRepository = moodRepository;
        this.userRepository = userRepository;
        this.contentService = contentService;
        this.socialRepository = socialRepository;
        this.popularityRanking = popularityRanking;
    }

    @Transactional
    public RecordResponse create(Long userId, CreateRecordRequest request) {
        Content content = contentService.resolveOrCreate(request.provider(), request.externalId());

        // 불변식 1 — 재기록은 새 기록이 아니라 수정으로 유도한다
        recordRepository.findActiveByUserAndContent(userId, content.getId()).ifPresent(existing -> {
            throw new LitmoodException(
                    ErrorCode.RECORD_DUPLICATE, "이미 기록한 콘텐츠입니다. 기존 기록을 수정해 주세요");
        });

        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new LitmoodException(ErrorCode.UNAUTHORIZED));

        // 공개 범위를 지정하지 않으면 사용자의 기본값을 따른다 (F-03-07)
        Visibility visibility =
                request.visibility() != null ? request.visibility() : user.getDefaultVisibility();

        Record record = Record.create(userId, content, request.status(), visibility);
        record.changeRating(request.rating());
        record.edit(
                request.review(),
                request.isSpoiler(),
                visibility,
                request.contextNote(),
                request.startedAt(),
                request.finishedAt(),
                false, // 새로 만드는 기록이라 지울 날짜가 없다
                false,
                request.repeatCount());
        record.replaceMoods(resolveMoods(request.moods()));

        Record saved = recordRepository.save(record);
        // 인기 랭킹은 부가 기능이라 실패해도 기록 생성을 막지 않는다 (F-07-02)
        popularityRanking.record(content.getId());

        // 방금 만든 본인 기록이므로 좋아요는 아직 없다
        return RecordResponse.from(saved, Set.of());
    }

    @Transactional
    public RecordResponse update(Long userId, Long recordId, UpdateRecordRequest request) {
        Record record = loadOwned(userId, recordId);

        if (request.status() != null) {
            record.changeStatus(request.status());
        }
        // status 를 WANT 로 바꾸면 changeStatus 가 별점을 지운다.
        // 그 뒤에 별점을 적용해야 "WANT + 별점" 요청이 규칙대로 거부된다.
        if (Boolean.TRUE.equals(request.clearRating())) {
            // 상태는 그대로 두고 별점만 지운다. rating: null 은 "변경 없음"이라
            // 지움을 표현할 수단이 따로 있어야 한다.
            record.changeRating(null);
        } else if (request.rating() != null) {
            record.changeRating(request.rating());
        }
        if (request.moods() != null) {
            record.replaceMoods(resolveMoods(request.moods()));
        }
        record.edit(
                request.review(),
                request.isSpoiler(),
                request.visibility(),
                request.contextNote(),
                request.startedAt(),
                request.finishedAt(),
                Boolean.TRUE.equals(request.clearStartedAt()),
                Boolean.TRUE.equals(request.clearFinishedAt()),
                request.repeatCount());

        return RecordResponse.from(record, likedByMe(userId, List.of(record)));
    }

    @Transactional
    public void delete(Long userId, Long recordId) {
        loadOwned(userId, recordId).softDelete();
    }

    @Transactional(readOnly = true)
    public RecordResponse get(Long viewerId, Long recordId) {
        Record record = recordRepository
                .findActiveById(recordId)
                .orElseThrow(() -> LitmoodException.notFound("기록"));

        boolean follows = socialRepository.isFollowing(viewerId, record.getUserId());
        boolean blocked = socialRepository.isBlockedBetween(viewerId, record.getUserId());

        if (!record.isVisibleTo(viewerId, follows) || blocked) {
            // 존재 여부를 숨기기 위해 403 이 아닌 404 로 응답한다
            throw LitmoodException.notFound("기록");
        }
        return RecordResponse.from(record, likedByMe(viewerId, List.of(record)));
    }

    /** 내 타임라인 (F-04-01). 본인 조회이므로 모든 공개 범위를 포함한다. */
    @Transactional(readOnly = true)
    public RecordPage timeline(Long userId, TimelineFilter filter, String cursorToken, Integer limit) {
        return page(userId, List.of(Visibility.values()), userId, filter, cursorToken, limit);
    }

    /**
     * 공개 프로필의 기록 목록 (F-03-07).
     * 조회자가 팔로워면 FOLLOWERS 기록까지 보인다.
     */
    @Transactional(readOnly = true)
    public RecordPage publicTimeline(
            String handle, Long viewerId, TimelineFilter filter, String cursorToken, Integer limit) {
        User owner = userRepository
                .findActiveByHandle(handle)
                .orElseThrow(() -> LitmoodException.notFound("사용자"));

        if (socialRepository.isBlockedBetween(viewerId, owner.getId())) {
            throw LitmoodException.notFound("사용자");
        }
        if (viewerId != null && viewerId.equals(owner.getId())) {
            return page(owner.getId(), List.of(Visibility.values()), viewerId, filter, cursorToken, limit);
        }

        List<Visibility> visibleTo = socialRepository.isFollowing(viewerId, owner.getId())
                ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS)
                : List.of(Visibility.PUBLIC);

        return page(owner.getId(), visibleTo, viewerId, filter, cursorToken, limit);
    }

    /** 팔로잉 피드 (F-06-02). 팔로우한 사용자들의 공개·팔로워 공개 기록. */
    @Transactional(readOnly = true)
    public RecordPage feed(Long viewerId, TimelineFilter filter, String cursorToken, Integer limit) {
        List<Long> followees = socialRepository.findFolloweeIds(viewerId);
        if (followees.isEmpty()) {
            return new RecordPage(List.of(), null, 0);
        }

        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Optional<Cursor> cursor = Cursor.decode(cursorToken);

        RecordQuery query = new RecordQuery(
                followees,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS),
                socialRepository.findHiddenUserIds(viewerId),
                filter.types(),
                filter.statuses(),
                normalizeMoodNames(filter.moods()),
                filter.minRating(),
                filter.from(),
                filter.to(),
                cursor.map(Cursor::createdAt).orElse(null),
                cursor.map(Cursor::id).orElse(null),
                size + 1);

        return toPage(recordRepository.findTimeline(query), size, viewerId, query);
    }

    private RecordPage page(
            Long ownerId,
            List<Visibility> visibleTo,
            Long viewerId,
            TimelineFilter filter,
            String cursorToken,
            Integer limit) {

        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Optional<Cursor> cursor = Cursor.decode(cursorToken);

        RecordQuery query = RecordQuery.forOwner(
                ownerId,
                visibleTo,
                filter.types(),
                filter.statuses(),
                normalizeMoodNames(filter.moods()),
                filter.minRating(),
                filter.from(),
                filter.to(),
                cursor.map(Cursor::createdAt).orElse(null),
                cursor.map(Cursor::id).orElse(null),
                // 다음 페이지 존재 여부를 알기 위해 1건 더 가져온다
                size + 1);

        return toPage(recordRepository.findTimeline(query), size, viewerId, query);
    }

    private RecordPage toPage(List<Record> found, int size, Long viewerId, RecordQuery query) {
        boolean hasMore = found.size() > size;
        List<Record> pageItems = hasMore ? found.subList(0, size) : found;

        String nextCursor = null;
        if (hasMore) {
            Record last = pageItems.get(pageItems.size() - 1);
            nextCursor = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }

        // "내가 누른 좋아요"를 한 번의 질의로 판정한다 — 항목마다 조회하면 N+1 이다
        Set<Long> liked = likedByMe(viewerId, pageItems);

        return new RecordPage(
                pageItems.stream().map(record -> RecordResponse.from(record, liked)).toList(),
                nextCursor,
                recordRepository.countTimeline(query));
    }

    private Set<Long> likedByMe(Long viewerId, List<Record> records) {
        if (viewerId == null || records.isEmpty()) {
            return Set.of();
        }
        return socialRepository.findLikedTargetIds(
                viewerId, LikeTarget.RECORD, records.stream().map(Record::getId).toList());
    }

    /**
     * 무드 이름을 엔티티로 변환한다. 없는 무드는 자유 입력으로 새로 만든다 (F-03-04).
     * 정규화된 이름이 같으면 같은 무드다 (불변식 5).
     */
    private Set<Mood> resolveMoods(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Set.of();
        }
        if (rawNames.size() > Record.MAX_MOODS) {
            throw new LitmoodException(ErrorCode.MOOD_LIMIT_EXCEEDED);
        }

        // 입력 순서를 보존하되 중복은 제거한다
        List<String> normalized = rawNames.stream()
                .map(Mood::normalize)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();

        Map<String, Mood> existing = moodRepository.findAllByNames(normalized).stream()
                .collect(java.util.stream.Collectors.toMap(Mood::getName, mood -> mood));

        Set<Mood> resolved = new LinkedHashSet<>();
        for (int i = 0; i < normalized.size(); i++) {
            String name = normalized.get(i);
            Mood mood = existing.get(name);
            if (mood == null) {
                mood = moodRepository.save(Mood.ofFreeform(rawNames.get(i)));
            }
            resolved.add(mood);
        }
        return resolved;
    }

    private List<String> normalizeMoodNames(List<String> raw) {
        return raw == null ? null : raw.stream().map(Mood::normalize).filter(n -> !n.isBlank()).toList();
    }

    private Record loadOwned(Long userId, Long recordId) {
        Record record = recordRepository
                .findActiveById(recordId)
                .orElseThrow(() -> LitmoodException.notFound("기록"));
        if (!record.isOwnedBy(userId)) {
            throw new LitmoodException(ErrorCode.FORBIDDEN, "본인의 기록만 수정할 수 있습니다");
        }
        return record;
    }

    /** 타임라인 필터 (F-04-02). */
    public record TimelineFilter(
            List<ContentType> types,
            List<RecordStatus> statuses,
            List<String> moods,
            BigDecimal minRating,
            LocalDate from,
            LocalDate to) {}
}
