package com.litmood.infrastructure.storage;

import com.litmood.application.port.AvatarStorage;
import com.litmood.infrastructure.config.LitmoodProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 호환 스토리지(로컬은 MinIO) 기반 구현 (docs/06-infra.md).
 *
 * <p>presign 은 서명 계산만 하는 오프라인 연산이라 스토리지에 접속하지 않는다 —
 * 발급 자체는 네트워크 실패 없이 항상 빠르다.
 */
@Component
public class S3AvatarStorage implements AvatarStorage {

    /** 만료가 짧을수록 URL 유출 시 위험이 줄어든다. 파일 선택 직후 올리는 흐름이라 10분이면 충분하다. */
    private static final Duration EXPIRY = Duration.ofMinutes(10);

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    private final S3Presigner presigner;
    private final String bucket;
    private final String publicUrlPrefix;

    public S3AvatarStorage(LitmoodProperties properties) {
        LitmoodProperties.Storage storage = properties.storage();
        this.bucket = storage.bucket();
        this.publicUrlPrefix = storage.endpoint() + "/" + storage.bucket() + "/";
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.US_EAST_1) // MinIO 는 리전을 쓰지 않지만 SDK 가 값을 요구한다
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
                // MinIO 는 가상 호스트 방식(bucket.host)을 로컬에서 해석하지 못한다
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Override
    public PresignedUpload presignAvatarUpload(long userId, String contentType) {
        String key = "avatars/%d/%s.%s".formatted(userId, UUID.randomUUID(), EXTENSIONS.get(contentType));

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                // 서명에 포함되므로 클라이언트가 다른 형식으로 바꿔 올릴 수 없다
                .contentType(contentType)
                .build();

        String uploadUrl = presigner
                .presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(EXPIRY)
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();

        return new PresignedUpload(uploadUrl, publicUrlPrefix + key);
    }

    @Override
    public String publicUrlPrefix() {
        return publicUrlPrefix;
    }
}
