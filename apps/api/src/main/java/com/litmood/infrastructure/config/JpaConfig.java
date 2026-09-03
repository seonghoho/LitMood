package com.litmood.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** JPA 구현체는 infrastructure 레이어에만 존재한다 (docs/03-architecture.md). */
@Configuration
@EnableJpaRepositories(basePackages = "com.litmood.infrastructure.persistence")
public class JpaConfig {}
