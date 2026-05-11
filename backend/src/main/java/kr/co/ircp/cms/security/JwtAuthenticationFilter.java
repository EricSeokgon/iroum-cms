package kr.co.ircp.cms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JWT Bearer Token 인증 필터.
 *
 * <p>SPEC-CMS-002 Step 3 REFACTOR — Stateless JWT 인증을 Spring Security 필터 체인에 통합.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>Authorization 헤더 없거나 Bearer 접두 아닌 경우 → 통과 (anon 처리)
 *   <li>블랙리스트 확인 → 해당 시 401
 *   <li>토큰 유효성 검증 → 만료 시 401 + {code: "TOKEN_EXPIRED"}, 서명 불일치 시 401
 *   <li>클레임 추출 → SecurityContext에 {@link JwtPrincipal} 설정
 * </ol>
 */
// @MX:ANCHOR: [AUTO] JwtAuthenticationFilter.doFilterInternal — 모든 요청의 JWT 인증 진입점
// @MX:REASON: SecurityConfig, TokenBlacklistMapper, JwtTokenProvider 참조 (fan_in >= 3)
// @MX:WARN: [AUTO] 요청마다 TokenBlacklistMapper.exists(DB 조회) 호출 — 성능 병목 가능
// @MX:REASON: 블랙리스트 조회가 인증 요청마다 DB I/O를 수행함. 트래픽 증가 시 Redis 캐시 레이어 도입 검토 필요
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistMapper tokenBlacklistMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            TokenBlacklistMapper tokenBlacklistMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistMapper = tokenBlacklistMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 1. Bearer 토큰 없으면 통과 (익명 요청 또는 Public 엔드포인트)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // 2. 블랙리스트 확인 (로그아웃된 토큰)
        if (tokenBlacklistMapper.exists(HashUtil.sha256Hex(token))) {
            sendUnauthorized(response, "TOKEN_REVOKED", "로그아웃된 토큰입니다");
            return;
        }

        // 3. 토큰 검증 및 클레임 추출
        Optional<JwtTokenProvider.JwtClaims> claimsOpt;
        try {
            claimsOpt = jwtTokenProvider.validateAccessToken(token);
        } catch (TokenExpiredException e) {
            sendUnauthorized(response, "TOKEN_EXPIRED", "토큰이 만료되었습니다");
            return;
        }

        if (claimsOpt.isEmpty()) {
            // 서명 불일치 또는 파싱 실패
            sendUnauthorized(response, "TOKEN_INVALID", "유효하지 않은 토큰입니다");
            return;
        }

        // 4. SecurityContext 설정
        // @MX:WARN: [AUTO] authorities 폭증 방지 — roles + permissions 합산 시 토큰 크기 주의
        // @MX:REASON: 권한 수가 많아지면 JWT 크기 및 SecurityContext 메모리 증가. 현재 15개 권한으로 허용 범위.
        JwtTokenProvider.JwtClaims claims = claimsOpt.get();
        Set<String> roles = claims.roles();
        Set<String> permissions = claims.permissions();
        JwtPrincipal principal = new JwtPrincipal(claims.userId(), claims.username(), roles, permissions);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        // ROLE_ 접두사 역할 권한 (hasRole, hasAnyRole 패턴 호환)
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        // 권한 코드 직접 등록 (hasAuthority 패턴 사용 가능 — REQ-AUTH-013)
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        // SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-003 — username PII 제거 (userId만 출력)
        // username(이메일/로그인ID)은 PII이므로 디버그 로그에서 제외하고 비식별 식별자(userId)만 남긴다.
        log.debug("JWT 인증 완료: userId={}", claims.userId());

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"code\":\"%s\",\"message\":\"%s\",\"traceId\":null}", code, message));
    }
}
