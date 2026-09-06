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
     * 공개 URL 이 가리키는 객체를 지운다.
     *
     * <p>아바타를 바꾸거나 지울 때 이전 객체를 정리하는 데 쓴다. 실패하면 예외를 던진다 —
     * 이것을 무시할지 말지는 호출부가 정한다. 정리는 부가 작업이고 프로필 저장을
     * 실패시킬 이유가 없다.
     */
    void deleteAvatar(String publicUrl);

    /**
     * @param uploadUrl 클라이언트가 PUT 할 서명된 URL (만료 있음)
     * @param publicUrl 업로드 완료 후 영구적으로 읽을 수 있는 URL
     */
    record PresignedUpload(String uploadUrl, String publicUrl) {}
}
