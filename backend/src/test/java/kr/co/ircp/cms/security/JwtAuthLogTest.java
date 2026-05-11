package kr.co.ircp.cms.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 디버그 로그 PII 정정 검증 — SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-003.
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-MASK-003-1: log.debug 출력에 username(이메일/로그인ID)이 포함되지 않고
 *                     userId만 기록된다.</li>
 * </ul>
 *
 * <p>Logback {@link ListAppender}로 실시간 로그 이벤트를 캡처하여 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — debug 로그 PII 제거 (REQ-PII-MASK-003)")
class JwtAuthLogTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    TokenBlacklistMapper tokenBlacklistMapper;

    @Mock
    FilterChain filterChain;

    JwtAuthenticationFilter filter;

    /** JwtAuthenticationFilter의 Logback 로거 (캡처 대상). */
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistMapper);
        SecurityContextHolder.clearContext();

        // ListAppender 부착 — DEBUG 레벨 강제
        filterLogger = (Logger) LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);

        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
        filterLogger.setLevel(originalLevel);
        appender.stop();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AC-MASK-003-1: 인증 성공 debug 로그에 username이 포함되지 않고 userId만 기록된다")
    void debug_log_excludes_username_includes_userId() throws Exception {
        // given
        String token = "valid-jwt-token";
        String sensitiveUsername = "alice@example.com"; // PII (이메일)
        long userId = 42L;

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();

        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, sensitiveUsername, Set.of("EDITOR"), Set.of("EDITOR"),
                Instant.now().plusSeconds(900));

        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(token)).thenReturn(Optional.of(claims));

        // when
        filter.doFilterInternal(req, res, filterChain);

        // then — DEBUG 레벨 이벤트 중 "JWT 인증 완료" 메시지를 찾는다
        List<ILoggingEvent> events = appender.list;
        ILoggingEvent authCompleteEvent = events.stream()
                .filter(e -> e.getLevel() == Level.DEBUG)
                .filter(e -> e.getFormattedMessage().contains("JWT 인증 완료"))
                .findFirst()
                .orElse(null);

        assertThat(authCompleteEvent)
                .as("JWT 인증 완료 debug 로그가 기록되어야 함")
                .isNotNull();

        String formatted = authCompleteEvent.getFormattedMessage();

        // userId는 포함되어야 함 (식별자, PII 아님)
        assertThat(formatted).contains(String.valueOf(userId));

        // username(이메일)은 포함되면 안 됨 (PII 차단)
        assertThat(formatted)
                .as("debug 로그에 username(PII)이 누출되면 안 됨")
                .doesNotContain(sensitiveUsername)
                .doesNotContain("username=")
                .doesNotContain("alice");
    }
}
