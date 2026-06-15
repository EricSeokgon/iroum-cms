package kr.co.ircp.cms.config;

import kr.co.ircp.cms.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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
import org.springframework.security.web.header.writers.StaticHeadersWriter;
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
            // SPEC-CMS-SECURITY-MEDIUM-14 — Stateless JWT API 의 CSRF 비활성화 + 보상 통제.
            // <p>JWT Access Token 은 Authorization 헤더(Bearer)로 전송되므로 본질적으로 CSRF 안전하다.
            //    Refresh Token 은 쿠키로 운반되지만 AuthController#buildRefreshCookie 에서
            //    HttpOnly + Secure + SameSite=Strict 속성을 강제하여 CSRF 공격을 차단한다.
            //    또한 CORS 정책에서 명시적 Origin 만 허용(HIGH-6)하여 cross-site 요청을 봉쇄한다.
            //    따라서 CSRF 필터의 동기화 토큰은 불필요하다.
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
                // WARN-2 수정: Permissions-Policy — 민감 브라우저 API 비활성화
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=(), fullscreen=(self)"))
            )
            // Stateless 세션 (JWT 사용)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 요청별 접근 제어
            .authorizeHttpRequests(auth -> auth
                // SPEC-CMS-SECURITY-LOW-17/18/19 — Actuator 엔드포인트 권한 분리.
                // health 만 PUBLIC 으로 노출하고, 그 외 모든 actuator 경로는 ADMIN 권한 필요.
                // info(LOW-17): 환경 정보 노출 방지 (management.info.env.enabled=false 와 함께)
                // backupStatus(LOW-18): 백업 상태 정보는 운영 민감 정보로 ADMIN 전용
                // metrics/prometheus/loggers(LOW-19): 내부 메트릭/로거 정보 ADMIN 전용
                // 안전망: 미처리 예외가 Tomcat /error 로 dispatch 될 때 anonymous 접근 허용
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers(
                    "/api/v1/health/**",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    // 공개 사이트 시민 회원가입 (anonymous)
                    "/api/v1/auth/register",
                    // REQ-AUTH-017 — 본인인증 및 비밀번호 재설정 (anonymous)
                    "/api/v1/auth/verify/request",
                    "/api/v1/auth/verify/confirm",
                    "/api/v1/auth/password/reset-request",
                    "/api/v1/auth/password/reset-confirm",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // REQ-BOARD-001~003: 게시판·게시글·댓글 목록·상세 공개 조회 허용
                // 실제 백엔드 경로: /api/v1/board/masters/**, /api/v1/board/posts/**
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/board/masters/**",
                    "/api/v1/board/posts/**",
                    "/api/v1/board/comments/**"
                ).permitAll()
                // REQ-BOARD-007: FAQ 공개 조회 허용 (목록·카테고리·단건)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/faqs/**"
                ).permitAll()
                // REQ-BOARD-008: Q&A 목록·단건 공개 조회 (비공개 글은 서비스 레이어에서 접근 제어)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/qnas",
                    "/api/v1/qnas/**"
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
                // REQ-CONTENT-005-D: 시민용 slug 기반 페이지 공개 조회
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/content/pages/by-slug/**"
                ).permitAll()
                // REQ-CONTENT-005: 메뉴 트리 공개 조회
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/content/menus/**"
                ).permitAll()
                // REQ-CONTENT-008-D: 팝업 공개 조회 (active)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/content/popups/**"
                ).permitAll()
                // REQ-CONTENT-009-D: 배너 공개 조회
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/content/banners/**"
                ).permitAll()
                // REQ-CONTENT-009-D: 배너 클릭 (익명)
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/content/banners/*/click"
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
                // SPEC-CMS-AI-002 REQ-PM-001/012 — 하이브리드 정책 추천·피드백 공개 API
                // (비회원 허용, 회원이면 인증 컨텍스트 활용. AI-001 비회원 화이트리스트 패턴)
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/ai/policy-match",
                    "/api/v1/ai/policy-match/feedback"
                ).permitAll()
                // SPEC-CMS-AI-003 REQ-RAG-001/013 — RAG 질의·피드백 공개 API
                // (비회원 허용, 회원이면 인증 컨텍스트 활용. AI-002 화이트리스트 패턴)
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/ai/rag/query",
                    "/api/v1/ai/rag/feedback"
                ).permitAll()
                // SPEC-CMS-AI-004 REQ-AI-TAG-007/NFR-003 — 태그 추천·피드백 공개 API
                // (시민 비인증 허용, 관리자면 인증 컨텍스트 활용. AI-002/003 화이트리스트 패턴)
                // @MX:WARN: [AUTO] 규칙 순서 중요 — /api/v1/ai/** authenticated() 규칙보다 반드시 위에 위치해야 함
                // @MX:REASON: 아래 .requestMatchers("/api/v1/ai/**").authenticated() 가 먼저 매칭되면 시민 비인증 추천이 401로 차단됨
                // @MX:SPEC: SPEC-CMS-AI-004
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/ai/tag-recommend",
                    "/api/v1/ai/tag-recommend/feedback"
                ).permitAll()
                // REQ-POLICY-001: 정책사업 목록·단건 공개 조회 허용
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/policy/programs",
                    "/api/v1/policy/programs/**"
                ).permitAll()
                // SPEC-CMS-AI-001 — AI 운영자 API는 ADMIN 전용(defense-in-depth, @PreAuthorize와 이중화)
                .requestMatchers("/api/v1/admin/ai/**").hasRole("ADMIN")
                // SPEC-CMS-AI-001 — AI 예측/시뮬레이션은 인증 사용자 전용
                .requestMatchers("/api/v1/ai/**").authenticated()
                // SPEC-CMS-006 REQ-SAFE-001: 안전 가이드라인·사고사례 공개 조회 허용
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/safety/guidelines/**",
                    "/api/v1/safety/incidents",
                    "/api/v1/safety/incidents/**"
                ).permitAll()
                // REQ-MEDIA-PUBLIC: 미디어 갤러리 공개 조회 허용 (업로드·고아 관리는 아래에서 별도 제한)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/media"
                ).permitAll()
                // REQ-STATS-PUBLIC: 공개 대시보드 위젯 데이터 조회 허용 (PublicStatsView 사용)
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/dashboard/widgets/**"
                ).permitAll()
                // REQ-MEDIA-004-D-1: 미디어 업로드 EDITOR+ 전용 (DEPT_ADMIN/ADMIN 포함, SUPER_ADMIN은 RoleHierarchy 자동 승격)
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/media/upload"
                ).hasAnyRole("EDITOR", "DEPT_ADMIN", "ADMIN")
                // REQ-MEDIA-004-D-3: 고아 자산 목록·정리는 ADMIN 전용
                .requestMatchers(
                    org.springframework.http.HttpMethod.GET,
                    "/api/v1/media/orphans"
                ).hasRole("ADMIN")
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/v1/media/orphans/cleanup"
                ).hasRole("ADMIN")
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

    // SPEC-CMS-RBAC-001 REQ-RBAC-001 — 역할 계층 완성.
    // 상위 역할은 하위 역할의 모든 권한을 자동 상속한다(SUPER_ADMIN ⊃ ADMIN ⊃ DEPT_ADMIN ⊃ EDITOR ⊃ VIEWER).
    // Spring Security 6.4+: RoleHierarchy @Bean 선언만으로 @PreAuthorize + URL 룰 모두 자동 적용.
    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy(
            "ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_DEPT_ADMIN > ROLE_EDITOR > ROLE_VIEWER");
        return hierarchy;
    }

    // @PreAuthorize 표현식에 RoleHierarchy 적용 (static — 빈 초기화 순서 보장)
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
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
