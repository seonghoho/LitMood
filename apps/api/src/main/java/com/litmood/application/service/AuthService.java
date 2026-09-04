package com.litmood.application.service;

import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.UserRepository;
import com.litmood.infrastructure.security.JwtTokenProvider;
import com.litmood.infrastructure.security.RefreshTokenStore;
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.AuthDtos.LoginRequest;
import com.litmood.interfaces.dto.AuthDtos.SignupRequest;
import com.litmood.interfaces.dto.AuthDtos.UserSummary;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F-01 — 가입 / 로그인 / 토큰 회전. 트랜잭션 경계는 이 레이어가 갖는다. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public Issued signup(SignupRequest request) {
        if (userRepository.existsActiveByEmail(request.email())) {
            throw new LitmoodException(ErrorCode.EMAIL_TAKEN);
        }
        if (userRepository.existsActiveByHandle(request.handle())) {
            throw new LitmoodException(ErrorCode.HANDLE_TAKEN);
        }

        User user = userRepository.save(User.ofEmail(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.handle(),
                request.nickname()));

        return issue(user);
    }

    @Transactional(readOnly = true)
    public Issued login(LoginRequest request) {
        User user = userRepository
                .findActiveByEmail(request.email())
                .orElseThrow(() -> new LitmoodException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"));

        // 존재하지 않는 계정과 비밀번호 불일치를 같은 메시지로 응답해 계정 존재 여부를 노출하지 않는다.
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new LitmoodException(ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");
        }

        return issue(user);
    }

    /**
     * 토큰 회전 (ADR-009).
     * 이미 폐기된 jti 로 요청이 오면 탈취 후 재사용으로 간주하고 전 세션을 무효화한다.
     */
    @Transactional(readOnly = true)
    public Issued refresh(String refreshToken) {
        Claims claims = tokenProvider.parse(refreshToken);
        Long userId = tokenProvider.userIdOf(claims);
        String jti = claims.getId();

        if (!refreshTokenStore.isValid(userId, jti)) {
            log.warn("refresh 토큰 재사용 감지 — userId={} jti={}", userId, jti);
            refreshTokenStore.revokeAll(userId);
            throw new LitmoodException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        refreshTokenStore.revoke(userId, jti);

        User user = userRepository.findById(userId).orElseThrow(() -> new LitmoodException(ErrorCode.UNAUTHORIZED));
        return issue(user);
    }

    public void logout(String refreshToken) {
        try {
            Claims claims = tokenProvider.parse(refreshToken);
            refreshTokenStore.revoke(tokenProvider.userIdOf(claims), claims.getId());
        } catch (LitmoodException e) {
            // 이미 만료·무효한 토큰으로의 로그아웃은 성공으로 간주한다 (멱등).
            log.debug("무효한 토큰으로 로그아웃 요청됨");
        }
    }

    private Issued issue(User user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getHandle());
        JwtTokenProvider.RefreshToken refresh = tokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.store(user.getId(), refresh.jti(), refresh.ttl());

        return new Issued(
                new AuthResponse(accessToken, tokenProvider.accessTtlSeconds(), UserSummary.from(user)),
                refresh.value(),
                refresh.ttl().toSeconds());
    }

    /** 응답 본문과, 쿠키로 나가야 하는 refresh 토큰을 분리해 전달한다. */
    public record Issued(AuthResponse body, String refreshToken, long refreshMaxAge) {}
}
