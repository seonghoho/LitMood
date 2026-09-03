package com.litmood.infrastructure.security;

import com.litmood.infrastructure.config.LitmoodProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * ADR-009 — JWT 무상태 인증.
 * K8s 에서 API 파드가 수평 확장되므로 서버 세션을 두지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LitmoodProperties properties;

    public SecurityConfig(LitmoodProperties properties) {
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()) // 무상태 + Bearer 토큰이므로 CSRF 토큰이 불필요하다
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 공개 엔드포인트 — SEO 대상 페이지가 소비한다 (NFR-06)
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/api/v1/ping")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents/**", "/api/v1/moods/**", "/api/v1/discover/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/@*/**", "/api/v1/collections/*")
                        .permitAll()
                        .anyRequest()
                        .authenticated());

        // TODO(M1): JwtAuthenticationFilter 등록 — 토큰 검증 및 SecurityContext 주입
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        config.setAllowCredentials(true); // refresh 토큰 쿠키 전송에 필요 (ADR-009)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /** cost 12 — F-01-01. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
