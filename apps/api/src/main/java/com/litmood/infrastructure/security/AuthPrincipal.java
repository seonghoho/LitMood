package com.litmood.infrastructure.security;

/** SecurityContext 에 담기는 인증 주체. 엔티티를 싣지 않아 세션 유지 비용이 없다. */
public record AuthPrincipal(Long userId, String handle) {}
