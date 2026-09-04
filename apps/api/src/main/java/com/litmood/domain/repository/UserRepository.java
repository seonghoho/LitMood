package com.litmood.domain.repository;

import com.litmood.domain.model.User;
import java.util.Optional;

/**
 * domain 은 인터페이스만 정의하고 infrastructure 가 구현한다.
 * 이 규칙 덕분에 영속화 기술을 바꿔도 도메인은 재컴파일되지 않는다
 * (docs/03-architecture.md).
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findActiveByEmail(String email);

    Optional<User> findActiveByHandle(String handle);

    boolean existsActiveByEmail(String email);

    boolean existsActiveByHandle(String handle);
}
