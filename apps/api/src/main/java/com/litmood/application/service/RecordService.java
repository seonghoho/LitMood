package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Content;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.Mood;
import com.litmood.domain.model.ProviderType;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.RecordStatus;
import com.litmood.domain.model.User;
import com.litmood.domain.model.Visibility;
import com.litmood.domain.repository.ContentRepository;
import com.litmood.domain.repository.MoodRepository;
import com.litmood.domain.repository.RecordQuery;
import com.litmood.domain.repository.RecordRepository;
import com.litmood.domain.repository.UserRepository;
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
    private final ContentRepository contentRepository;
    private final MoodRepository moodRepository;
    private final UserRepository userRepository;
    private final ContentSearchService contentSearchService;

    public RecordService(
            RecordRepository recordRepository,
            ContentRepository contentRepository,
            MoodRepository moodRepository,
            UserRepository userRepository,
            ContentSearchService contentSearchService) {
        this.recordRepository = recordRepository;
        this.contentRepository = contentRepository;
        this.moodRepository = moodRepository;
        this.userRepository = userRepository;
        this.contentSearchService = contentSearchService;
    }

    @Transactional
    public RecordResponse create(Long userId, CreateRecordRequest request) {
        Content content = resolveContent(request.provider(), request.externalId());

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
                request.repeatCount());
        record.replaceMoods(resolveMoods(request.moods()));

        return RecordResponse.from(recordRepository.save(record));
    }

    @Transactional
    public RecordResponse update(Long userId, Long recordId, UpdateRecordRequest request) {
        Record record = loadOwned(userId, recordId);

        if (request.status() != null) {
            record.changeStatus(request.status());
        }
        // status 를 WANT 로 바꾸면 changeStatus 가 별점을 지운다.
        // 그 뒤에 별점을 적용해야 "WANT + 별점" 요청이 규칙대로 거부된다.
        if (request.rating() != null) {
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
                request.repeatCount());

        return RecordResponse.from(record);
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

        // 팔로우 관계는 M4 에서 구현한다. 그전까지 FOLLOWERS 는 본인에게만 보인다.
        if (!record.isVisibleTo(viewerId, false)) {
            // 존재 여부를 숨기기 위해 403 이 아닌 404 로 응답한다
            throw LitmoodException.notFound("기록");
        }
        return RecordResponse.from(record);
    }

    /** 내 타임라인 (F-04-01). 본인 조회이므로 모든 공개 범위를 포함한다. */
    @Transactional(readOnly = true)
    public RecordPage timeline(Long userId, TimelineFilter filter, String cursorToken, Integer limit) {
        return page(userId, List.of(Visibility.values()), filter, cursorToken, limit);
    }

    /** 공개 프로필의 기록 목록. PUBLIC 만 노출한다 (F-03-07). */
    @Transactional(readOnly = true)
    public RecordPage publicTimeline(String handle, TimelineFilter filter, String cursorToken, Integer limit) {
        User owner = userRepository
                .findActiveByHandle(handle)
                .orElseThrow(() -> LitmoodException.notFound("사용자"));
        return page(owner.getId(), List.of(Visibility.PUBLIC), filter, cursorToken, limit);
    }

    private RecordPage page(
            Long ownerId,
            List<Visibility> visibleTo,
            TimelineFilter filter,
            String cursorToken,
            Integer limit) {

        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Optional<Cursor> cursor = Cursor.decode(cursorToken);

        RecordQuery query = new RecordQuery(
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

        List<Record> found = recordRepository.findTimeline(query);

        boolean hasMore = found.size() > size;
        List<Record> pageItems = hasMore ? found.subList(0, size) : found;

        String nextCursor = null;
        if (hasMore) {
            Record last = pageItems.get(pageItems.size() - 1);
            nextCursor = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }

        return new RecordPage(
                pageItems.stream().map(RecordResponse::from).toList(),
                nextCursor,
                recordRepository.countTimeline(query));
    }

    /**
     * 콘텐츠 스냅샷 확보 (F-02-05).
     * 이미 있으면 재사용하고, 없으면 provider 에서 가져와 자체 DB 에 저장한다.
     */
    private Content resolveContent(ProviderType provider, String externalId) {
        return contentRepository
                .findByProviderAndExternalId(provider, externalId)
                .orElseGet(() -> {
                    ContentSnapshot snapshot = contentSearchService
                            .findByExternalId(provider, externalId)
                            .orElseThrow(() -> new LitmoodException(
                                    ErrorCode.PROVIDER_UNAVAILABLE, "콘텐츠 정보를 가져오지 못했습니다"));
                    return contentRepository.save(Content.from(snapshot));
                });
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
