package kr.co.ircp.cms.config;

import kr.co.ircp.cms.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 (Step 3 REFACTOR).
 *
 * <p>SPEC-CMS-002 — JwtAuthenticationFilter 통합 + Method Security (@PreAuthorize) 활성화.
 * 기존 Step 0 bean 구조를 유지하면서 JWT 필터 체인을 추가한다.
 */
@Configuration
@EnableWebSecurity
// @PreAuthorize, @PostAuthorize 지원 — SPEC-CMS-002 REQ-AUTH-008 메뉴 권한 검사 준비
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    // @MX:ANCHOR: [AUTO] SecurityFilterChain — 전체 보안 정책의 진입점. 변경 시 인증·인가 흐름 전체 영향
    // @MX:REASON: JwtAuthenticationFilter, CorsConfig, 세션 정책, EntryPoint 등 fan_in >= 3
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter) throws Exception {

        http
            // Stateless REST API — CSRF 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            // Stateless 세션 (JWT 사용)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 요청별 접근 제어
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/health/**",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    // REQ-AUTH-017 — 본인인증 및 비밀번호 재설정 (anonymous)
                    "/api/v1/auth/verify/request",
                    "/api/v1/auth/verify/confirm",
                    "/api/v1/auth/password/reset-request",
                    "/api/v1/auth/password/reset-confirm",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/backupStatus"
                ).permitAll()
                // REQ-BOARD-001~003: 게시판·게시글·댓글 목록·상세 공개 조회 허용
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/boards/**"
                ).permitAll()
                // REQ-BOARD-007: FAQ 공개 조회 허용 (목록·카테고리·단건)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/faqs/**"
                ).permitAll()
                // REQ-BOARD-012: 발간자료 공개 조회 허용 (목록·카테고리·단건)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/publications/**"
                ).permitAll()
                // REQ-BOARD-012-D-4: ZIP 다운로드 요청 (익명 허용)
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/publications/*/download-zip"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 삽입
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // 인증/인가 실패 응답 커스터마이징
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"code\":\"AUTH_REQUIRED\",\"message\":\"인증이 필요합니다\",\"traceId\":null}"
                    );
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write(
                        "{\"code\":\"AUTH_FORBIDDEN\",\"message\":\"권한이 없습니다\",\"traceId\":null}"
                    );
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt strength 12 — tech.md §4 보안 구성 요소 준수
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            kr.co.ircp.cms.domain.auth.service.JwtTokenProvider jwtTokenProvider,
            kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper tokenBlacklistMapper) {
        return new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistMapper);
    }
}
