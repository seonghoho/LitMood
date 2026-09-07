package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.User;
import com.litmood.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    UserRepositoryImpl(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<User> findAllByIds(List<Long> ids) {
        // IN () 은 SQL 문법 오류다. 빈 목록은 질의 없이 끝낸다.
        return ids.isEmpty() ? List.of() : jpaRepository.findAllById(ids);
    }

    @Override
    public Optional<User> findActiveByEmail(String email) {
        return jpaRepository.findActiveByEmail(email);
    }

    @Override
    public Optional<User> findActiveByHandle(String handle) {
        return jpaRepository.findActiveByHandle(handle);
    }

    @Override
    public boolean existsActiveByEmail(String email) {
        return jpaRepository.existsActiveByEmail(email);
    }

    @Override
    public boolean existsActiveByHandle(String handle) {
        return jpaRepository.existsActiveByHandle(handle);
    }
}
