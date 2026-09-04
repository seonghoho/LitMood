package com.litmood.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 커서 페이지네이션의 불투명 토큰 (docs/05-api-spec.md).
 *
 * 클라이언트는 이 값을 파싱하지 않는다 — 내부 표현을 바꿔도 API 계약이 깨지지 않도록
 * Base64 로 감싼다. 잘못된 커서는 예외 대신 "처음부터"로 처리해,
 * 오래된 링크를 열었을 때 에러 화면 대신 첫 페이지를 보여준다.
 */
public record Cursor(Instant createdAt, Long id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<Cursor> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(SEPARATOR);
            if (separator < 0) {
                return Optional.empty();
            }
            return Optional.of(new Cursor(
                    Instant.parse(raw.substring(0, separator)), Long.valueOf(raw.substring(separator + 1))));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
