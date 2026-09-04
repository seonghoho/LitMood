package com.litmood.interfaces.controller;

import com.litmood.application.service.AuthService;
import com.litmood.application.service.AuthService.Issued;
import com.litmood.domain.exception.ErrorCode;
import com.litmood.domain.exception.LitmoodException;
import com.litmood.interfaces.dto.AuthDtos.AuthResponse;
import com.litmood.interfaces.dto.AuthDtos.LoginRequest;
import com.litmood.interfaces.dto.AuthDtos.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증")
@SecurityRequirements // 인증 엔드포인트 자체는 토큰을 요구하지 않는다
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "이메일 회원가입")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return respond(authService.signup(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "액세스 토큰 재발급", description = "refresh 토큰을 회전시킨다")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new LitmoodException(ErrorCode.UNAUTHORIZED, "로그인 정보가 없습니다");
        }
        return respond(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> respond(Issued issued, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued).toString())
                .body(issued.body());
    }

    /**
     * ADR-009 — refresh 토큰은 JS 가 읽을 수 없는 쿠키로만 오간다.
     * path 를 /api/v1/auth 로 좁혀 다른 API 요청에는 실려 나가지 않게 한다.
     */
    private ResponseCookie refreshCookie(Issued issued) {
        return baseCookie(issued.refreshToken()).maxAge(issued.refreshMaxAge()).build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/v1/auth");
    }
}
