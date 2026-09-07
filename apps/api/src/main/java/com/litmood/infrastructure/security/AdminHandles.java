package com.litmood.infrastructure.security;

import com.litmood.domain.repository.UserRepository;
import com.litmood.infrastructure.config.LitmoodProperties;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 운영자 판정 (#28).
 *
 * <p>권한을 {@code users} 테이블이 아니라 환경변수 목록에 둔다. DB 를 직접 고치는 대신
 * 배포로 바뀌고, 시크릿과 같은 경로로 관리된다. 대신 두 가지를 감수한다 —
 * 권한 부여·회수에 재배포가 필요하고, 회수는 이미 발급된 access 토큰이 만료된 뒤
 * (기본 15분) 실제로 적용된다.
 *
 * <p>핸들은 <b>대소문자를 구분해</b> 비교한다. 가입 규칙({@code ^[a-zA-Z0-9_]{3,30}$})이
 * 대소문자를 보존하므로 {@code admin} 과 {@code Admin} 은 서로 다른 계정이고,
 * 느슨하게 맞추면 비슷한 핸들을 만든 사람에게 권한이 새어 나간다.
 */
@Component
public class AdminHandles {

    private static final Logger log = LoggerFactory.getLogger(AdminHandles.class);

    private final Set<String> handles;
    private final UserRepository userRepository;

    public AdminHandles(LitmoodProperties properties, UserRepository userRepository) {
        List<String> configured =
                properties.admin() == null || properties.admin().handles() == null
                        ? List.of()
                        : properties.admin().handles();
        this.handles = configured.stream()
                .map(String::trim)
                .filter(handle -> !handle.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.userRepository = userRepository;
    }

    public boolean isAdmin(String handle) {
        return handle != null && handles.contains(handle);
    }

    /**
     * 없는 핸들이 목록에 적혀 있으면 경고한다.
     *
     * <p>핸들은 가입 순서로 임자가 정해진다. 아직 존재하지 않는 핸들을 적어 두면
     * 그 이름으로 먼저 가입하는 사람이 운영자가 되므로, 오타를 조용히 넘기면 안 된다.
     */
    @EventListener(ApplicationReadyEvent.class)
    void warnOnUnknownHandles() {
        if (handles.isEmpty()) {
            log.info("운영자 핸들이 설정되지 않았습니다 — /api/v1/admin/** 에 접근할 수 있는 계정이 없습니다");
            return;
        }
        handles.stream()
                .filter(handle -> userRepository.findActiveByHandle(handle).isEmpty())
                .forEach(handle -> log.warn(
                        "운영자로 설정된 핸들 '{}' 에 해당하는 계정이 없습니다. "
                                + "오타가 아니라면, 이 핸들로 먼저 가입하는 사람이 운영자가 됩니다",
                        handle));
    }
}
