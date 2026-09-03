package com.litmood.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.litmood.application.port.ContentProvider;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import com.litmood.infrastructure.config.LitmoodProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 책 — 네이버 책 검색 API. 국내 도서 커버리지가 가장 넓다 (ADR-010). */
@Component
public class NaverBookProvider implements ContentProvider {

    private static final DateTimeFormatter PUBDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final LitmoodProperties.Provider.Naver config;

    public NaverBookProvider(RestClient.Builder builder, LitmoodProperties properties) {
        this.config = properties.provider().naver();
        this.restClient = builder.baseUrl(config.baseUrl()).build();
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.NAVER_BOOK;
    }

    @Override
    public boolean isConfigured() {
        return hasText(config.clientId()) && hasText(config.clientSecret());
    }

    @Override
    public List<ContentSnapshot> search(String query, int limit) {
        JsonNode response = restClient
                .get()
                .uri(uri -> uri.path("/v1/search/book.json")
                        .queryParam("query", query)
                        .queryParam("display", Math.min(limit, 100))
                        .build())
                .header("X-Naver-Client-Id", config.clientId())
                .header("X-Naver-Client-Secret", config.clientSecret())
                .retrieve()
                .body(JsonNode.class);

        return toSnapshots(response);
    }

    @Override
    public Optional<ContentSnapshot> findByExternalId(String isbn) {
        JsonNode response = restClient
                .get()
                .uri(uri -> UriComponentsBuilder.fromPath("/v1/search/book_adv.json")
                        .queryParam("d_isbn", isbn)
                        .build()
                        .toUri())
                .header("X-Naver-Client-Id", config.clientId())
                .header("X-Naver-Client-Secret", config.clientSecret())
                .retrieve()
                .body(JsonNode.class);

        return toSnapshots(response).stream().findFirst();
    }

    private List<ContentSnapshot> toSnapshots(JsonNode response) {
        List<ContentSnapshot> results = new ArrayList<>();
        if (response == null || !response.has("items")) {
            return results;
        }
        for (JsonNode item : response.get("items")) {
            results.add(toSnapshot(item));
        }
        return results;
    }

    private ContentSnapshot toSnapshot(JsonNode item) {
        // 네이버는 검색어 강조용 <b> 태그를 응답에 넣는다. 저장 전에 제거한다.
        String title = stripTags(text(item, "title"));
        String isbn = text(item, "isbn");

        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "isbn13", isbn);
        putIfPresent(metadata, "publisher", stripTags(text(item, "publisher")));
        putIfPresent(metadata, "link", text(item, "link"));

        return new ContentSnapshot(
                ContentType.BOOK,
                ProviderType.NAVER_BOOK,
                isbn,
                title,
                splitAuthors(stripTags(text(item, "author"))),
                parseDate(text(item, "pubdate")),
                emptyToNull(text(item, "image")),
                stripTags(text(item, "description")),
                metadata);
    }

    /** 네이버는 복수 저자를 '^' 로 구분한다. */
    private List<String> splitAuthors(String author) {
        if (author == null || author.isBlank()) {
            return List.of();
        }
        return Arrays.stream(author.split("\\^")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private LocalDate parseDate(String pubdate) {
        if (pubdate == null || pubdate.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(pubdate, PUBDATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String stripTags(String value) {
        return value == null ? null : value.replaceAll("<[^>]*>", "");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
