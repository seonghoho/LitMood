package com.litmood.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 엔티티는 domain 에, JPA 구현체는 infrastructure 에 둔다
 * (docs/03-architecture.md 레이어 규칙).
 */
@Configuration
@EntityScan(basePackages = "com.litmood.domain.model")
@EnableJpaRepositories(basePackages = "com.litmood.infrastructure.persistence")
public class JpaConfig {}
