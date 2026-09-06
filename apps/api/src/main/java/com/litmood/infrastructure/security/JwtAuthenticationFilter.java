package com.litmood.infrastructure.security;

import com.litmood.domain.exception.LitmoodException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰을 검증해 SecurityContext 를 채운다.
 *
 * 토큰이 없거나 잘못된 경우에도 여기서 예외를 던지지 않는다 —
 * 공개 엔드포인트는 인증 없이 통과해야 하므로, 접근 거부 판단은
 * 뒤따르는 인가 단계에 맡긴다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AdminHandles adminHandles;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AdminHandles adminHandles) {
        this.tokenProvider = tokenProvider;
        this.adminHandles = adminHandles;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parse(token);
                String handle = tokenProvider.handleOf(claims);
                // 운영자 여부는 토큰이 아니라 설정에서 온다 (#28).
                //   토큰에 넣으면 권한을 회수해도 남은 토큰이 refresh TTL(2주) 동안 살아 있다.
                boolean admin = adminHandles.isAdmin(handle);
                AuthPrincipal principal = new AuthPrincipal(tokenProvider.userIdOf(claims), handle, admin);

                var authorities = admin
                        ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (LitmoodException e) {
                // 만료·위조 토큰은 익명으로 취급한다. 보호된 자원 접근 시 401 이 된다.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
