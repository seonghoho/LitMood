package com.litmood.domain.repository;

import com.litmood.domain.model.User;
import java.util.List;
import java.util.Optional;

/**
 * domain 은 인터페이스만 정의하고 infrastructure 가 구현한다.
 * 이 규칙 덕분에 영속화 기술을 바꿔도 도메인은 재컴파일되지 않는다
 * (docs/03-architecture.md).
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /**
     * 여러 사용자를 한 번에. 탈퇴한 계정도 함께 돌려준다 —
     * 신고 큐(#28)는 이미 사라진 대상·신고자도 "삭제됨" 으로 보여줘야 한다.
     */
    List<User> findAllByIds(List<Long> ids);

    Optional<User> findActiveByEmail(String email);

    Optional<User> findActiveByHandle(String handle);

    boolean existsActiveByEmail(String email);

    boolean existsActiveByHandle(String handle);
}
