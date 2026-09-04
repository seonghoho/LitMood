package com.litmood.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.AuthDtos.SignupRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** 인증이 필요한 통합 테스트의 공통 도구. */
public abstract class AuthenticatedTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected TestRestTemplate rest;

    /** 매번 새 사용자를 만들어 테스트 간 데이터가 섞이지 않게 한다. */
    protected AuthResponse signupNewUser() {
        int n = SEQUENCE.incrementAndGet();
        String handle = "user_" + n + "_" + System.nanoTime() % 100000;
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/v1/auth/signup",
                new SignupRequest(handle + "@litmood.test", "password123", handle, "사용자" + n),
                AuthResponse.class);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected HttpHeaders bearer(AuthResponse auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.accessToken());
        return headers;
    }

    protected <T> ResponseEntity<T> authed(
            AuthResponse auth, HttpMethod method, String path, Object body, Class<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, bearer(auth)), type);
    }

    protected ResponseEntity<java.util.Map<String, Object>> authedMap(
            AuthResponse auth, HttpMethod method, String path, Object body) {
        return rest.exchange(
                path, method, new HttpEntity<>(body, bearer(auth)), new ParameterizedTypeReference<>() {});
    }
}
