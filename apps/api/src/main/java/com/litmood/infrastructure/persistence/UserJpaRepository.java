package com.litmood.infrastructure.persistence;

import com.litmood.domain.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<User, Long> {

    // 이메일·핸들은 대소문자를 구분하지 않는다 (V1 의 lower() 부분 유니크 인덱스와 정합).
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE lower(u.handle) = lower(:handle) AND u.deletedAt IS NULL")
    Optional<User> findActiveByHandle(@Param("handle") String handle);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.email) = lower(:email) AND u.deletedAt IS NULL")
    boolean existsActiveByEmail(@Param("email") String email);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.handle) = lower(:handle) AND u.deletedAt IS NULL")
    boolean existsActiveByHandle(@Param("handle") String handle);
}
