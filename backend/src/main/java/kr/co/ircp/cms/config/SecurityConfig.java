package kr.co.ircp.cms.config;

import kr.co.ircp.cms.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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

    /**
     * CORS 허용 Origin 목록 (콤마 구분, 운영 환경에서 환경변수 주입 권장).
     *
     * <p>HIGH-6 — 와일드카드("*") 사용 금지, 명시적 Origin만 허용.
     * 기본값: 로컬 개발용 localhost 포트 두 개만 등록.
     */
    @Value("${iroum.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String corsAllowedOrigins;

    /**
     * HSTS max-age (초). 기본 1년. 운영(HTTPS)에서만 효과 발생.
     */
    @Value("${iroum.security.hsts.max-age-seconds:31536000}")
    private long hstsMaxAgeSeconds;

    /**
     * Content-Security-Policy 헤더 값. swagger-ui 호환을 위해 inline style 허용.
     * 운영에서는 nonce/hash 기반으로 더욱 엄격하게 조정 가능.
     */
    @Value("${iroum.security.csp:"
            + "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self' data:; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "frame-ancestors 'none'; "
            + "form-action 'self'}")
    private String contentSecurityPolicy;

    // @MX:ANCHOR: [AUTO] SecurityFilterChain — 전체 보안 정책의 진입점. 변경 시 인증·인가 흐름 전체 영향
    // @MX:REASON: JwtAuthenticationFilter, CorsConfig, 세션 정책, EntryPoint 등 fan_in >= 3
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter) throws Exception {

        http
            // Stateless REST API — CSRF 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            // CORS 설정 — HIGH-6: 명시적 Origin 만 허용
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 보안 응답 헤더 — HIGH-6: CSP / HSTS / X-Frame-Options / nosniff / Referrer-Policy
            .headers(headers -> headers
                .contentTypeOptions(opts -> {}) // X-Content-Type-Options: nosniff
                .frameOptions(frame -> frame.deny()) // X-Frame-Options: DENY
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(hstsMaxAgeSeconds))
                .referrerPolicy(rp -> rp.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
            )
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
                // REQ-BOARD-013: 설문조사 공개 조회 허용 (목록·단건·결과)
                // POST /surveys, PUT/DELETE /surveys/{id}, GET /surveys/{id}/results 는 @PreAuthorize 로 통제
                // POST /surveys/{id}/responses 는 익명 허용
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/surveys/**"
                ).permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/surveys/*/responses"
                ).permitAll()
                // SPEC-CMS-010 REQ-SEARCH-001/005/006/008: 통합 검색·자동완성·인기·클릭 PUBLIC
                // 단, /api/v1/search/synonyms 는 ADMIN 전용(@PreAuthorize 로 별도 통제)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/search",
                    "/api/v1/search/autocomplete",
                    "/api/v1/search/popular"
                ).permitAll()
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/search/click"
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

    /**
     * CORS 설정 — HIGH-6 보안 보강.
     *
     * <p>운영 환경에서는 iroum.security.cors.allowed-origins 환경변수로
     * 프론트엔드 도메인만 명시적으로 등록한다. 와일드카드("*") 절대 금지.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        // setAllowedOrigins 는 "*"" + allowCredentials(true) 조합을 거부 → 안전
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept",
                "X-Requested-With", "X-Forwarded-For", "X-CSRF-Token"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true); // refresh_token 쿠키 송수신 허용
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
