package com.litmood.infrastructure.security;

import com.litmood.infrastructure.config.LitmoodProperties;
import com.litmood.interfaces.advice.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(
            LitmoodProperties properties,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.properties = properties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
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
                        // "/v3/api-docs.yaml" 은 "/v3/api-docs/**" 에 매칭되지 않으므로 별도로 허용한다
                        .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/contents/**", "/api/v1/moods/**", "/api/v1/discover/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/@*/**", "/api/v1/collections/*")
                        .permitAll()
                        // 기록 단건은 공개 공유 대상이다. 숫자 id 로 한정해야
                        // "/api/v1/records/me" 까지 열리는 사고를 막는다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/records/{id:[0-9]+}")
                        .permitAll()
                        // 운영 화면 (#28). 판정 근거는 AdminHandles — 환경변수의 핸들 목록이다.
                        // 여기서 막는 것이 유일한 관문이므로, 컨트롤러에 다시 검사를 두지 않는다.
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated());

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Security 가 거부한 요청도 Problem Details 로 응답하게 한다
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(authenticationEntryPoint));

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
