package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;

import com.litmood.support.AuthenticatedTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 프로필 편집 + 아바타 업로드 (F-01-04, 이슈 #3). */
class UserProfileApiTest extends AuthenticatedTest {

    // application-test.yml 의 litmood.storage 값과 일치해야 한다
    private static final String STORAGE_PUBLIC_PREFIX = "http://localhost:9000/litmood/";

    @Nested
    @DisplayName("PATCH /users/me")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임·소개·기본 공개범위를 수정하고, GET /me 에 반영된다")
        void updatesProfileFields() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> patched = authedMap(
                    auth,
                    HttpMethod.PATCH,
                    "/api/v1/users/me",
                    Map.of("nickname", "새닉네임", "bio", "무드로 기록합니다", "defaultVisibility", "FOLLOWERS"));

            assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(patched.getBody())
                    .containsEntry("nickname", "새닉네임")
                    .containsEntry("bio", "무드로 기록합니다")
                    .containsEntry("defaultVisibility", "FOLLOWERS");

            ResponseEntity<Map<String, Object>> me =
                    authedMap(auth, HttpMethod.GET, "/api/v1/users/me", null);
            assertThat(me.getBody())
                    .containsEntry("nickname", "새닉네임")
                    .containsEntry("bio", "무드로 기록합니다")
                    .containsEntry("defaultVisibility", "FOLLOWERS");
        }

        @Test
        @DisplayName("우리 스토리지의 avatarUrl 은 저장된다")
        void acceptsAvatarUrlFromOurStorage() {
            var auth = signupNewUser();
            String avatarUrl = STORAGE_PUBLIC_PREFIX + "avatars/" + auth.user().id() + "/a.webp";

            ResponseEntity<Map<String, Object>> patched =
                    authedMap(auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", avatarUrl));

            assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(patched.getBody()).containsEntry("avatarUrl", avatarUrl);
        }

        @Test
        @DisplayName("빈 문자열이면 아바타가 지워진다 — 다른 필드와 같은 규칙 (이슈 #24)")
        void emptyAvatarUrlClearsIt() {
            var auth = signupNewUser();
            String avatarUrl = STORAGE_PUBLIC_PREFIX + "avatars/" + auth.user().id() + "/a.webp";
            authedMap(auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", avatarUrl));

            ResponseEntity<Map<String, Object>> cleared =
                    authedMap(auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", ""));

            assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cleared.getBody()).containsEntry("avatarUrl", null);
            // 스토리지가 없어도(테스트에는 MinIO 가 없다) 프로필 저장은 성공해야 한다 —
            // 이전 객체 정리는 부가 작업이라 실패를 삼킨다
            assertThat(authedMap(auth, HttpMethod.GET, "/api/v1/users/me", null).getBody())
                    .containsEntry("avatarUrl", null);
        }

        @Test
        @DisplayName("아바타를 바꿔도 다른 필드는 함께 유지된다 — 정리 실패가 저장을 막지 않는다")
        void replacingAvatarKeepsOtherFields() {
            var auth = signupNewUser();
            String first = STORAGE_PUBLIC_PREFIX + "avatars/" + auth.user().id() + "/first.webp";
            String second = STORAGE_PUBLIC_PREFIX + "avatars/" + auth.user().id() + "/second.webp";
            authedMap(auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", first, "nickname", "그대로"));

            ResponseEntity<Map<String, Object>> replaced =
                    authedMap(auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", second));

            assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(replaced.getBody())
                    .containsEntry("avatarUrl", second)
                    .containsEntry("nickname", "그대로");
        }

        @Test
        @DisplayName("스토리지 밖의 avatarUrl 은 400 — 임의 URL 주입 차단")
        void rejectsForeignAvatarUrl() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> patched = authedMap(
                    auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("avatarUrl", "https://evil.example/x.png"));

            assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(patched.getBody()).containsEntry("code", "VALIDATION_FAILED");
        }

        @Test
        @DisplayName("닉네임 50자 초과는 400")
        void rejectsTooLongNickname() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> patched = authedMap(
                    auth, HttpMethod.PATCH, "/api/v1/users/me", Map.of("nickname", "가".repeat(51)));

            assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("비로그인은 401")
        void requiresAuth() {
            ResponseEntity<String> response = rest.exchange(
                    "/api/v1/users/me",
                    HttpMethod.PATCH,
                    new HttpEntity<>(Map.of("nickname", "아무개")),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("POST /users/me/avatar")
    class AvatarUpload {

        @Test
        @DisplayName("presigned uploadUrl 과 publicUrl 을 발급한다")
        void issuesPresignedUrl() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> response = authedMap(
                    auth,
                    HttpMethod.POST,
                    "/api/v1/users/me/avatar",
                    Map.of("contentType", "image/png", "contentLength", 1024));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            String uploadUrl = (String) response.getBody().get("uploadUrl");
            String publicUrl = (String) response.getBody().get("publicUrl");

            // presigned PUT — 서명 파라미터가 포함되고, 사용자별 avatars/ 키를 가리킨다
            assertThat(uploadUrl).contains("X-Amz-Signature");
            assertThat(uploadUrl).contains("/avatars/" + auth.user().id() + "/");
            // publicUrl 은 서명 없이 공개 접근 가능한 경로
            assertThat(publicUrl)
                    .startsWith(STORAGE_PUBLIC_PREFIX + "avatars/" + auth.user().id() + "/")
                    .endsWith(".png")
                    .doesNotContain("X-Amz-Signature");
        }

        @Test
        @DisplayName("지원하지 않는 이미지 형식은 400 UNSUPPORTED_IMAGE_TYPE")
        void rejectsUnsupportedContentType() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> response = authedMap(
                    auth,
                    HttpMethod.POST,
                    "/api/v1/users/me/avatar",
                    Map.of("contentType", "image/gif", "contentLength", 1024));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "UNSUPPORTED_IMAGE_TYPE");
        }

        @Test
        @DisplayName("5MB 초과는 400 FILE_TOO_LARGE")
        void rejectsTooLargeFile() {
            var auth = signupNewUser();

            ResponseEntity<Map<String, Object>> response = authedMap(
                    auth,
                    HttpMethod.POST,
                    "/api/v1/users/me/avatar",
                    Map.of("contentType", "image/jpeg", "contentLength", 5 * 1024 * 1024 + 1));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "FILE_TOO_LARGE");
        }

        @Test
        @DisplayName("비로그인은 401")
        void requiresAuth() {
            ResponseEntity<String> response = rest.exchange(
                    "/api/v1/users/me/avatar",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("contentType", "image/png", "contentLength", 1024)),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
