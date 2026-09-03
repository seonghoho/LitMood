package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;

import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.AuthDtos.LoginRequest;
import com.litmood.interfaces.dto.AuthDtos.SignupRequest;
import com.litmood.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** F-01 — 가입 / 로그인 / 토큰 회전 / 재사용 감지 (ADR-009). */
class AuthFlowTest extends IntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("가입하면 액세스 토큰과 refresh 쿠키가 함께 발급된다")
    void signupIssuesTokens() {
        ResponseEntity<AuthResponse> response = signup("dawn@litmood.test", "dawn_reader");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(900);
        assertThat(response.getBody().user().handle()).isEqualTo("dawn_reader");

        // refresh 는 JS 가 읽을 수 없어야 한다
        String cookie = refreshCookieOf(response);
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth");
    }

    @Test
    @DisplayName("중복된 이메일로 가입하면 409 와 EMAIL_TAKEN 을 응답한다")
    void duplicateEmailRejected() {
        signup("dup@litmood.test", "first_handle");

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(new SignupRequest("dup@litmood.test", "password123", "second_handle", "두번째")),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "EMAIL_TAKEN");
    }

    @Test
    @DisplayName("잘못된 비밀번호는 계정 존재 여부를 노출하지 않고 401 을 응답한다")
    void wrongPasswordDoesNotLeakAccountExistence() {
        signup("secret@litmood.test", "secret_user");

        ResponseEntity<Map<String, Object>> wrongPassword = login("secret@litmood.test", "wrong-password");
        ResponseEntity<Map<String, Object>> unknownEmail = login("nobody@litmood.test", "whatever123");

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 두 경우의 응답이 구분되지 않아야 계정 열거(enumeration)를 막을 수 있다
        assertThat(wrongPassword.getBody()).isEqualTo(unknownEmail.getBody());
    }

    @Test
    @DisplayName("액세스 토큰으로 보호된 엔드포인트에 접근할 수 있다")
    void accessTokenGrantsAccess() {
        AuthResponse auth = signup("me@litmood.test", "me_user").getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.accessToken());
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/users/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("handle", "me_user");
    }

    @Test
    @DisplayName("refresh 는 토큰을 회전시키고, 폐기된 토큰 재사용은 전 세션을 무효화한다")
    void refreshRotatesAndDetectsReuse() {
        ResponseEntity<AuthResponse> signup = signup("rotate@litmood.test", "rotate_user");
        String firstRefresh = refreshTokenValueOf(signup);

        // 1) 정상 회전 — 새 토큰이 발급된다
        ResponseEntity<AuthResponse> refreshed = refresh(firstRefresh, AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondRefresh = refreshTokenValueOf(refreshed);
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // 2) 이미 폐기된 첫 토큰을 다시 사용 → 탈취로 간주
        ResponseEntity<Map<String, Object>> reuse = refresh(firstRefresh, null);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody()).containsEntry("code", "TOKEN_REUSE_DETECTED");

        // 3) 재사용 감지의 결과로, 정상적으로 회전됐던 두 번째 토큰까지 무효화된다
        ResponseEntity<Map<String, Object>> afterRevokeAll = refresh(secondRefresh, null);
        assertThat(afterRevokeAll.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("검증 실패는 필드별 사유를 담아 400 을 응답한다")
    void validationFailureIncludesFieldErrors() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/auth/signup",
                HttpMethod.POST,
                new HttpEntity<>(new SignupRequest("not-an-email", "short", "무효한핸들", "")),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(response.getBody().get("errors")).isInstanceOf(List.class);
    }

    // ── helpers ─────────────────────────────────────────────

    private ResponseEntity<AuthResponse> signup(String email, String handle) {
        return rest.postForEntity(
                "/api/v1/auth/signup",
                new SignupRequest(email, "password123", handle, handle),
                AuthResponse.class);
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return rest.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password)),
                new org.springframework.core.ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> refresh(String refreshToken, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshToken);
        if (type != null) {
            return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), type);
        }
        return (ResponseEntity<T>) rest.exchange(
                "/api/v1/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private String refreshCookieOf(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("refresh 쿠키가 없습니다"));
    }

    private String refreshTokenValueOf(ResponseEntity<?> response) {
        String cookie = refreshCookieOf(response);
        return cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
    }
}
