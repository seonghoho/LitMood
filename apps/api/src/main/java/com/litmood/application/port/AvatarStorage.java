package com.litmood.application.port;

/**
 * 아바타 이미지 저장소 추상화 (F-01-04).
 *
 * application 이 인터페이스를 소유하고 infrastructure 가 S3 호환 스토리지로 구현한다.
 * 이미지 바이트는 API 서버를 통과하지 않는다 — 클라이언트가 presigned URL 로 직접 올린다.
 */
public interface AvatarStorage {

    /**
     * 업로드용 presigned PUT URL 을 발급한다.
     *
     * <p>contentType 은 서명에 포함되므로, 발급받은 URL 로 다른 형식을 올릴 수 없다.
     */
    PresignedUpload presignAvatarUpload(long userId, String contentType);

    /** 이 스토리지가 서비스하는 공개 URL 접두사. 저장 요청이 온 URL 이 우리 것인지 판별하는 데 쓴다. */
    String publicUrlPrefix();

    /**
     * @param uploadUrl 클라이언트가 PUT 할 서명된 URL (만료 있음)
     * @param publicUrl 업로드 완료 후 영구적으로 읽을 수 있는 URL
     */
    record PresignedUpload(String uploadUrl, String publicUrl) {}
}
