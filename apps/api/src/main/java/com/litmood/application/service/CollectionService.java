package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Collection;
import com.litmood.domain.model.Content;
import com.litmood.domain.model.User;
import com.litmood.domain.model.Visibility;
import com.litmood.domain.repository.CollectionRepository;
import com.litmood.domain.repository.UserRepository;
import com.litmood.interfaces.dto.CollectionDtos.AddCollectionItemRequest;
import com.litmood.interfaces.dto.CollectionDtos.CollectionResponse;
import com.litmood.interfaces.dto.CollectionDtos.CollectionSummary;
import com.litmood.interfaces.dto.CollectionDtos.CreateCollectionRequest;
import com.litmood.interfaces.dto.CollectionDtos.ReorderItemsRequest;
import com.litmood.interfaces.dto.CollectionDtos.UpdateCollectionRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-05 — 컬렉션 큐레이션. */
@Service
public class CollectionService {

    private static final int MAX_LIST_SIZE = 50;

    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;
    private final ContentService contentService;

    public CollectionService(
            CollectionRepository collectionRepository,
            UserRepository userRepository,
            ContentService contentService) {
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
        this.contentService = contentService;
    }

    @Transactional
    public CollectionResponse create(Long userId, CreateCollectionRequest request) {
        String slug = uniqueSlug(request.title());
        Collection collection = Collection.create(
                userId, slug, request.title(), request.description(), request.visibility());
        return toResponse(collectionRepository.save(collection));
    }

    @Transactional(readOnly = true)
    public CollectionResponse get(Long viewerId, String slug) {
        Collection collection = collectionRepository
                .findActiveBySlugWithItems(slug)
                .orElseThrow(() -> LitmoodException.notFound("컬렉션"));

        // 팔로우는 M4 범위. 그전까지 FOLLOWERS 는 본인에게만 보인다.
        if (!collection.isVisibleTo(viewerId, false)) {
            // 존재 여부를 숨기기 위해 403 이 아닌 404 로 응답한다
            throw LitmoodException.notFound("컬렉션");
        }
        return toResponse(collection);
    }

    @Transactional(readOnly = true)
    public List<CollectionSummary> listByHandle(String handle, Long viewerId) {
        User owner = userRepository
                .findActiveByHandle(handle)
                .orElseThrow(() -> LitmoodException.notFound("사용자"));

        boolean self = viewerId != null && viewerId.equals(owner.getId());
        List<Visibility> visibleTo = self ? List.of(Visibility.values()) : List.of(Visibility.PUBLIC);

        return collectionRepository.findByOwner(owner.getId(), visibleTo, MAX_LIST_SIZE).stream()
                .map(CollectionSummary::from)
                .toList();
    }

    @Transactional
    public CollectionResponse update(Long userId, String slug, UpdateCollectionRequest request) {
        Collection collection = loadOwned(userId, slug);
        collection.edit(request.title(), request.description(), request.coverUrl(), request.visibility());
        return toResponse(collection);
    }

    @Transactional
    public void delete(Long userId, String slug) {
        loadOwned(userId, slug).softDelete();
    }

    @Transactional
    public CollectionResponse addItem(Long userId, String slug, AddCollectionItemRequest request) {
        Collection collection = loadOwned(userId, slug);
        // 기록하지 않은 콘텐츠도 담을 수 있다 (F-05 설계 원칙)
        Content content = contentService.resolveOrCreate(request.provider(), request.externalId());
        collection.addItem(content, request.note());
        return toResponse(collection);
    }

    @Transactional
    public CollectionResponse removeItem(Long userId, String slug, Long contentId) {
        Collection collection = loadOwned(userId, slug);
        collection.removeItem(contentId);
        return toResponse(collection);
    }

    @Transactional
    public CollectionResponse reorder(Long userId, String slug, ReorderItemsRequest request) {
        Collection collection = loadOwned(userId, slug);
        collection.reorder(request.contentIds());
        return toResponse(collection);
    }

    /**
     * slug 는 "제목-랜덤6자" 라 충돌이 사실상 없지만, 0 은 아니다.
     * 몇 번만 재시도하고 그래도 실패하면 명시적으로 알린다 — 조용히 덮어쓰는 것보다 낫다.
     */
    private String uniqueSlug(String title) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = Collection.generateSlug(title);
            if (!collectionRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new LitmoodException(ErrorCode.INTERNAL_ERROR, "컬렉션 주소를 만들지 못했습니다");
    }

    private Collection loadOwned(Long userId, String slug) {
        Collection collection = collectionRepository
                .findActiveBySlugWithItems(slug)
                .orElseThrow(() -> LitmoodException.notFound("컬렉션"));
        if (!collection.isOwnedBy(userId)) {
            throw new LitmoodException(ErrorCode.FORBIDDEN, "본인의 컬렉션만 수정할 수 있습니다");
        }
        return collection;
    }

    private CollectionResponse toResponse(Collection collection) {
        User owner = userRepository.findById(collection.getUserId()).orElse(null);
        return CollectionResponse.from(
                collection,
                owner == null ? null : owner.getHandle(),
                owner == null ? null : owner.getNickname());
    }
}
