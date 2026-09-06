package com.litmood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.litmood.application.port.AvatarStorage;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.infrastructure.config.LitmoodProperties;
import com.litmood.infrastructure.storage.S3AvatarStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** presign 은 서명 계산뿐이라 스토리지 없이 검증할 수 있다. */
class AvatarStorageTest {

    private static S3AvatarStorage storageWith(String accessKey, String secretKey) {
        return new S3AvatarStorage(new LitmoodProperties(
                null,
                null,
                null,
                new LitmoodProperties.Storage("http://localhost:9000", "litmood", accessKey, secretKey),
                null,
                null));
    }

    @Test
    @DisplayName("자격증명이 없어도 애플리케이션은 기동한다 — 아바타 때문에 API 전체가 죽으면 안 된다")
    void doesNotFailFastWithoutCredentials() {
        assertThatCode(() -> storageWith(null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자격증명이 없으면 발급 시점에만 실패한다")
    void failsOnlyWhenIssuing() {
        S3AvatarStorage storage = storageWith(null, null);

        assertThatThrownBy(() -> storage.presignAvatarUpload(1L, "image/png"))
                .isInstanceOf(LitmoodException.class);
    }

    @Test
    @DisplayName("키 경로와 확장자는 요청한 형식을 따른다")
    void buildsKeyFromContentType() {
        S3AvatarStorage storage = storageWith("key", "secret");

        AvatarStorage.PresignedUpload upload = storage.presignAvatarUpload(42L, "image/webp");

        assertThat(upload.publicUrl())
                .startsWith("http://localhost:9000/litmood/avatars/42/")
                .endsWith(".webp");
        assertThat(upload.uploadUrl()).contains("X-Amz-Signature");
    }
}
