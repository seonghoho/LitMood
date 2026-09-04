package com.litmood.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.litmood.application.port.ContentProvider;
import com.litmood.domain.model.ContentSnapshot;
import com.litmood.domain.model.ContentType;
import com.litmood.domain.model.ProviderType;
import com.litmood.infrastructure.config.LitmoodProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** 음악 — Spotify Web API. Client Credentials 로 서버 간 인증한다 (ADR-010). */
@Component
public class SpotifyMusicProvider implements ContentProvider {

    private final RestClient apiClient;
    private final RestClient authClient;
    private final LitmoodProperties.Provider.Spotify config;

    // Client Credentials 토큰은 1시간짜리다. 매 검색마다 재발급하면 왕복이 두 배가 된다.
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public SpotifyMusicProvider(RestClient.Builder builder, LitmoodProperties properties) {
        this.config = properties.provider().spotify();
        this.apiClient = builder.clone().baseUrl(config.baseUrl()).build();
        this.authClient = builder.clone().build();
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.SPOTIFY;
    }

    @Override
    public boolean isConfigured() {
        return hasText(config.clientId()) && hasText(config.clientSecret());
    }

    @Override
    public List<ContentSnapshot> search(String query, int limit) {
        JsonNode response = apiClient
                .get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("type", "track")
                        .queryParam("limit", Math.min(limit, 50))
                        .build())
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(JsonNode.class);

        List<ContentSnapshot> results = new ArrayList<>();
        if (response == null) {
            return results;
        }
        for (JsonNode item : response.path("tracks").path("items")) {
            results.add(toSnapshot(item));
        }
        return results;
    }

    @Override
    public Optional<ContentSnapshot> findByExternalId(String trackId) {
        JsonNode item = apiClient
                .get()
                .uri("/tracks/{id}", trackId)
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(JsonNode.class);

        return item == null ? Optional.empty() : Optional.of(toSnapshot(item));
    }

    private ContentSnapshot toSnapshot(JsonNode track) {
        JsonNode album = track.path("album");

        List<String> artists = new ArrayList<>();
        for (JsonNode artist : track.path("artists")) {
            artists.add(artist.path("name").asText());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("durationMs", track.path("duration_ms").asInt());
        putIfPresent(metadata, "album", album.path("name").asText(null));
        putIfPresent(metadata, "isrc", track.path("external_ids").path("isrc").asText(null));
        putIfPresent(metadata, "spotifyUrl", track.path("external_urls").path("spotify").asText(null));

        return new ContentSnapshot(
                ContentType.MUSIC,
                ProviderType.SPOTIFY,
                track.path("id").asText(),
                track.path("name").asText(),
                artists,
                parseReleaseDate(album),
                coverUrl(album),
                null,
                metadata);
    }

    /** Spotify 의 release_date 는 정밀도가 year / month / day 로 다르다. */
    private LocalDate parseReleaseDate(JsonNode album) {
        String value = album.path("release_date").asText(null);
        String precision = album.path("release_date_precision").asText("day");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return switch (precision) {
                case "year" -> LocalDate.of(Integer.parseInt(value), 1, 1);
                case "month" -> LocalDate.parse(value + "-01");
                default -> LocalDate.parse(value);
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String coverUrl(JsonNode album) {
        JsonNode images = album.path("images");
        return images.isArray() && !images.isEmpty() ? images.get(0).path("url").asText(null) : null;
    }

    private String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        tokenLock.lock();
        try {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            String basic = Base64.getEncoder()
                    .encodeToString((config.clientId() + ":" + config.clientSecret())
                            .getBytes(StandardCharsets.UTF_8));

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");

            JsonNode response = authClient
                    .post()
                    .uri(config.tokenUrl())
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("access_token")) {
                throw new IllegalStateException("Spotify 토큰 발급에 실패했습니다");
            }
            cachedToken = response.get("access_token").asText();
            // 만료 60초 전에 갱신해 경계에서의 401 을 피한다
            tokenExpiresAt = Instant.now().plusSeconds(response.path("expires_in").asLong(3600) - 60);
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
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
