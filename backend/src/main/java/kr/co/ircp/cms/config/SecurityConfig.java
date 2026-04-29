package kr.co.ircp.cms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 기본 설정 (Step 0 bootstrap).
 *
 * <p>JWT 필터 체인은 SPEC-CMS-002 인증 구현 단계에서 추가된다.
 * 현재는 헬스 체크·Swagger UI를 익명 접근 허용하고, 나머지는 인증 요구.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    // @MX:ANCHOR: [AUTO] SecurityFilterChain — 전체 보안 정책의 진입점. 변경 시 인증·인가 흐름 전체 영향
    // @MX:REASON: 이 빈은 JwtAuthenticationFilter, CorsConfig, 세션 정책을 통합한다 (fan_in >= 3)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API — CSRF 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless 세션 (JWT 사용)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 요청별 접근 제어
            .authorizeHttpRequests(auth -> auth
                // 헬스 체크 — 익명 허용
                .requestMatchers("/api/v1/health").permitAll()
                // 인증 API — 익명 허용 (SPEC-CMS-002 REQ-AUTH-001~003)
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout"
                ).permitAll()
                // Spring Boot Actuator — 익명 허용 (운영에서는 내부망 제한)
                .requestMatchers("/actuator/**").permitAll()
                // Swagger UI — 익명 허용 (prod 프로파일에서는 비활성화)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()
                // 나머지 모든 요청 — 인증 필요 (SPEC-CMS-002에서 JWT 필터 추가)
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt strength 12 — tech.md §4 보안 구성 요소 준수
        return new BCryptPasswordEncoder(12);
    }
}
