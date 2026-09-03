package com.litmood.application.service;

import com.litmood.application.port.AvatarStorage;
import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.User;
import com.litmood.domain.model.Visibility;
import com.litmood.domain.repository.UserRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 프로필 관리 (F-01-04). */
@Service
public class UserService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final UserRepository userRepository;
    private final AvatarStorage avatarStorage;

    public UserService(UserRepository userRepository, AvatarStorage avatarStorage) {
        this.userRepository = userRepository;
        this.avatarStorage = avatarStorage;
    }

    @Transactional(readOnly = true)
    public User me(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new LitmoodException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 프로필 수정. PATCH 이므로 넘어오지 않은(null) 필드는 그대로 둔다.
     *
     * <p>핸들은 여기서 바꾸지 않는다 — 공개 URL(/@handle)이 바뀌면 이미 공유된 링크가 깨진다.
     */
    @Transactional
    public User updateProfile(
            Long userId, String nickname, String bio, Visibility defaultVisibility, String avatarUrl) {
        User user = me(userId);

        user.updateProfile(
                nickname != null ? nickname : user.getNickname(),
                bio != null ? bio : user.getBio(),
                defaultVisibility != null ? defaultVisibility : user.getDefaultVisibility());

        if (avatarUrl != null) {
            // 우리 스토리지가 아닌 URL 을 그대로 저장하면 외부 이미지를 프로필로 걸 수 있게 된다
            if (!avatarUrl.startsWith(avatarStorage.publicUrlPrefix())) {
                throw new LitmoodException(ErrorCode.VALIDATION_FAILED, "아바타 이미지는 업로드한 것만 사용할 수 있습니다");
            }
            user.updateAvatar(avatarUrl);
        }

        return user;
    }

    /** 아바타 업로드용 presigned URL 발급. 이미지 바이트는 API 서버를 거치지 않는다. */
    public AvatarStorage.PresignedUpload issueAvatarUpload(Long userId, String contentType, long contentLength) {
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new LitmoodException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (contentLength > MAX_AVATAR_BYTES) {
            throw new LitmoodException(ErrorCode.FILE_TOO_LARGE);
        }
        return avatarStorage.presignAvatarUpload(userId, contentType);
    }
}
