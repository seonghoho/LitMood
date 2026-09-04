package com.litmood.application.service;

import com.litmood.application.port.ContentProvider;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import com.litmood.infrastructure.config.LitmoodProperties;
import com.litmood.infrastructure.redis.SearchCache;
import com.litmood.interfaces.dto.ContentDtos.ContentSummary;
import com.litmood.interfaces.dto.ContentDtos.SearchResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * F-02 — 통합 검색.
 *
 * 세 provider 를 <b>동시에</b> 호출한다. 순차 호출이면 지연이 합산되지만
 * 병렬이면 가장 느린 하나에 수렴한다.
 *
 * 가상 스레드를 쓰는 이유(ADR-003): 이 작업은 CPU 가 아니라 네트워크 대기가 전부다.
 * 플랫폼 스레드 풀이면 동시 검색 수만큼 OS 스레드를 점유하지만, 가상 스레드는
 * 대기 중 캐리어 스레드를 반납한다. WebFlux 로 갔다면 코드 전체가 리액티브로
 * 오염됐겠지만, 여기서는 아래처럼 평범한 명령형 코드로 남는다.
 *
 * 한 provider 가 죽어도 나머지 결과는 반환한다 (NFR-03).
 */
@Service
public class ContentSearchService {

    private static final Logger log = LoggerFactory.getLogger(ContentSearchService.class);
    private static final int DEFAULT_LIMIT = 10;

    private final Map<ContentType, ContentProvider> providersByType = new EnumMap<>(ContentType.class);
    private final SearchCache searchCache;
    private final Duration providerTimeout;

    public ContentSearchService(
            List<ContentProvider> providers, SearchCache searchCache, LitmoodProperties properties) {
        for (ContentProvider provider : providers) {
            providersByType.put(provider.contentType(), provider);
        }
        this.searchCache = searchCache;
        this.providerTimeout = Duration.ofMillis(properties.provider().timeoutMs());
    }

    public SearchResponse search(String query, List<ContentType> types, Integer limit) {
        List<ContentType> targets = types == null || types.isEmpty() ? List.of(ContentType.values()) : types;
        int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, 50);

        Map<ContentType, List<ContentSummary>> results = new EnumMap<>(ContentType.class);
        List<ProviderType> failed = Collections.synchronizedList(new ArrayList<>());
        List<ContentType> toFetch = new ArrayList<>();
        boolean allCached = true;

        // 1) 캐시 우선 조회 — 외부 API rate limit 과 지연을 함께 줄인다 (F-02-04)
        for (ContentType type : targets) {
            ContentProvider provider = providersByType.get(type);
            if (provider == null || !provider.isConfigured()) {
                log.debug("provider 미설정으로 건너뜀: {}", type);
                results.put(type, List.of());
                continue;
            }
            Optional<List<ContentSummary>> cached = searchCache.get(type, query, size);
            if (cached.isPresent()) {
                results.put(type, cached.get());
            } else {
                toFetch.add(type);
                allCached = false;
            }
        }

        if (toFetch.isEmpty()) {
            return new SearchResponse(results, List.of(), allCached);
        }

        // 2) 캐시 미스인 provider 만 병렬 호출
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<ProviderResult>> tasks = toFetch.stream()
                    .map(type -> (Callable<ProviderResult>) () -> fetch(type, query, size))
                    .toList();

            // invokeAll 의 타임아웃은 전체에 걸린다 — 느린 하나가 나머지를 잡아먹지 않는다
            List<Future<ProviderResult>> futures =
                    executor.invokeAll(tasks, providerTimeout.toMillis(), TimeUnit.MILLISECONDS);

            for (int i = 0; i < futures.size(); i++) {
                ContentType type = toFetch.get(i);
                ProviderType providerType = providersByType.get(type).providerType();
                try {
                    ProviderResult result = futures.get(i).get();
                    results.put(type, result.items());
                    searchCache.put(type, query, size, result.items());
                } catch (Exception e) {
                    // 타임아웃·장애 모두 부분 실패로 처리한다. 나머지 탭은 정상 노출된다.
                    log.warn("provider 호출 실패: {} ({})", providerType, e.getClass().getSimpleName());
                    results.put(type, List.of());
                    failed.add(providerType);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("검색이 중단되었습니다", e);
        }

        return new SearchResponse(results, List.copyOf(failed), false);
    }

    /** 기록 생성 시 콘텐츠 스냅샷을 얻는다 (F-02-05). */
    public Optional<ContentSnapshot> findByExternalId(ProviderType providerType, String externalId) {
        ContentProvider provider = providersByType.get(providerType.contentType());
        if (provider == null || !provider.isConfigured()) {
            return Optional.empty();
        }
        return provider.findByExternalId(externalId);
    }

    private ProviderResult fetch(ContentType type, String query, int limit) {
        ContentProvider provider = providersByType.get(type);
        List<ContentSummary> items =
                provider.search(query, limit).stream().map(ContentSummary::from).toList();
        return new ProviderResult(provider.providerType(), items);
    }

    private record ProviderResult(ProviderType provider, List<ContentSummary> items) {}
}
