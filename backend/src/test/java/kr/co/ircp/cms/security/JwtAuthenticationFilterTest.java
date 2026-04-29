package kr.co.ircp.cms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationFilter 단위 테스트 (Step 3 REFACTOR).
 *
 * <p>Mock 기반 — DB/컨텍스트 없이 필터 분기 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    TokenBlacklistMapper tokenBlacklistMapper;

    @Mock
    FilterChain filterChain;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistMapper);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더 없으면 SecurityContext 설정 없이 다음 필터로 통과")
    void doFilter_doesNotSetAuthentication_whenNoHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 접두 아닌 Authorization 헤더는 통과")
    void doFilter_doesNotSetAuthentication_whenInvalidFormat() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("블랙리스트에 존재하는 토큰 → 401 응답")
    void doFilter_returns401_whenBlacklisted() throws Exception {
        String token = "blacklisted-token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(tokenBlacklistMapper.exists(HashUtil.sha256Hex(token))).thenReturn(true);

        filter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("TOKEN_REVOKED");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("만료된 토큰 → 401 + TOKEN_EXPIRED 코드")
    void doFilter_returns401_whenExpired() throws Exception {
        String token = "expired-token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(token)).thenThrow(new TokenExpiredException());

        filter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("TOKEN_EXPIRED");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("서명 불일치 토큰 → 401 + TOKEN_INVALID 코드")
    void doFilter_returns401_whenSignatureMismatch() throws Exception {
        String token = "invalid-sig-token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(Optional.empty());

        filter.doFilterInternal(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("TOKEN_INVALID");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("유효한 JWT → SecurityContext에 JwtPrincipal 설정 + 다음 필터 통과")
    void doFilter_setsAuthentication_whenValidJwt() throws Exception {
        String token = "valid-jwt-token";
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                42L, "testuser", Set.of("EDITOR"), Set.of("EDITOR"), Instant.now().plusSeconds(900));

        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(Optional.of(claims));

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);

        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("testuser");
        assertThat(principal.roles()).containsExactly("EDITOR");
    }
}
