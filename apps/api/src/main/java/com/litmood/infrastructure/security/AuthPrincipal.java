package com.litmood.infrastructure.security;

/**
 * SecurityContext 에 담기는 인증 주체. 엔티티를 싣지 않아 세션 유지 비용이 없다.
 *
 * <p>{@code admin} 은 요청마다 {@link AdminHandles} 로 판정한 결과다 — 토큰 클레임이
 * 아니므로, 목록에서 빠진 운영자는 남은 토큰으로도 관리자 화면에 들어오지 못한다.
 */
public record AuthPrincipal(Long userId, String handle, boolean admin) {}
