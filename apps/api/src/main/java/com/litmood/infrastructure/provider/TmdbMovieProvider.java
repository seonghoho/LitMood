package com.litmood.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.litmood.application.port.ContentProvider;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import com.litmood.infrastructure.config.LitmoodProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 영화 — TMDB. 한국어 메타데이터 품질이 좋고 rate limit 이 관대하다 (ADR-010). */
@Component
public class TmdbMovieProvider implements ContentProvider {

    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
    private static final String LANGUAGE = "ko-KR";

    private final RestClient restClient;
    private final LitmoodProperties.Provider.Tmdb config;

    public TmdbMovieProvider(RestClient.Builder builder, LitmoodProperties properties) {
        this.config = properties.provider().tmdb();
        this.restClient = builder.baseUrl(config.baseUrl()).build();
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.TMDB;
    }

    @Override
    public boolean isConfigured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    @Override
    public List<ContentSnapshot> search(String query, int limit) {
        JsonNode response = restClient
                .get()
                .uri(uri -> uri.path("/search/movie")
                        .queryParam("api_key", config.apiKey())
                        .queryParam("query", query)
                        .queryParam("language", LANGUAGE)
                        .queryParam("include_adult", false)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<ContentSnapshot> results = new ArrayList<>();
        if (response == null || !response.has("results")) {
            return results;
        }
        for (JsonNode item : response.get("results")) {
            results.add(toSnapshot(item));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    @Override
    public Optional<ContentSnapshot> findByExternalId(String tmdbId) {
        JsonNode item = restClient
                .get()
                .uri(uri -> uri.path("/movie/{id}")
                        .queryParam("api_key", config.apiKey())
                        .queryParam("language", LANGUAGE)
                        .queryParam("append_to_response", "credits")
                        .build(tmdbId))
                .retrieve()
                .body(JsonNode.class);

        return item == null || item.has("success") ? Optional.empty() : Optional.of(toSnapshot(item));
    }

    private ContentSnapshot toSnapshot(JsonNode item) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tmdbId", item.path("id").asInt());
        putIfPresent(metadata, "originalTitle", text(item, "original_title"));
        if (item.has("runtime") && !item.get("runtime").isNull()) {
            metadata.put("runtime", item.get("runtime").asInt());
        }
        if (item.has("vote_average")) {
            metadata.put("tmdbRating", item.get("vote_average").asDouble());
        }

        return new ContentSnapshot(
                ContentType.MOVIE,
                ProviderType.TMDB,
                item.path("id").asText(),
                text(item, "title"),
                extractDirectorsOrCast(item),
                parseDate(text(item, "release_date")),
                posterUrl(text(item, "poster_path")),
                text(item, "overview"),
                metadata);
    }

    /**
     * 검색 응답에는 크레딧이 없다. 상세 조회(append_to_response=credits)일 때만 감독을 채우고,
     * 그렇지 않으면 빈 목록을 반환한다 — 검색 결과 1건마다 추가 호출을 하면 rate limit 을 소모한다.
     */
    private List<String> extractDirectorsOrCast(JsonNode item) {
        JsonNode crew = item.path("credits").path("crew");
        if (!crew.isArray()) {
            return List.of();
        }
        List<String> directors = new ArrayList<>();
        for (JsonNode member : crew) {
            if ("Director".equals(text(member, "job"))) {
                directors.add(text(member, "name"));
            }
        }
        return directors;
    }

    private String posterUrl(String posterPath) {
        return posterPath == null || posterPath.isBlank() ? null : IMAGE_BASE + posterPath;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
