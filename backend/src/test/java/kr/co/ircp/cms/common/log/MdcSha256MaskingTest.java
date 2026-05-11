package kr.co.ircp.cms.common.log;

import jakarta.servlet.FilterChain;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MdcLoggingFilter SHA-256 prefix 마스킹 검증 — SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002.
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-MASK-002-1: clientIp는 평문이 아닌 SHA-256 hex prefix(8 chars)로 MDC에 저장된다</li>
 *   <li>AC-MASK-002-2: userId는 평문 보존(PII 아님)</li>
 *   <li>AC-MASK-002-3: 동일 IP는 동일 prefix → 세션 추적성 보장</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MdcLoggingFilter — SHA-256 prefix 마스킹 (REQ-PII-MASK-002)")
class MdcSha256MaskingTest {

    @InjectMocks
    private MdcLoggingFilter filter;

    @AfterEach
    void cleanup() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AC-MASK-002-1: clientIp는 평문 IP가 아닌 SHA-256 hex prefix 8 chars로 저장된다")
    void clientIp_is_sha256_prefix() throws Exception {
        // given
        String plainIp = "10.20.30.40";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(plainIp);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedClientIp = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedClientIp.set(MDC.get("clientIp"));

        // when
        filter.doFilter(request, response, chain);

        // then — 평문 IP가 아니어야 함
        assertThat(capturedClientIp.get()).isNotEqualTo(plainIp);
        // SHA-256 hex prefix 8 chars (소문자 16진수) 형식 검증
        assertThat(capturedClientIp.get()).hasSize(8);
        assertThat(capturedClientIp.get()).matches("[0-9a-f]{8}");
        // 기대값: HashUtil.sha256Hex(plainIp).substring(0,8)
        String expected = HashUtil.sha256Hex(plainIp).substring(0, 8);
        assertThat(capturedClientIp.get()).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-MASK-002-1: X-Forwarded-For 헤더 IP도 SHA-256 prefix로 마스킹된다")
    void xff_header_ip_is_masked() throws Exception {
        // given
        String xffIp = "203.0.113.45";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", xffIp + ", 192.168.1.1");
        request.setRemoteAddr("10.0.0.1"); // 프록시 IP (사용 안됨)
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedClientIp = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedClientIp.set(MDC.get("clientIp"));

        // when
        filter.doFilter(request, response, chain);

        // then — XFF 첫 번째 IP의 해시 prefix
        String expected = HashUtil.sha256Hex(xffIp).substring(0, 8);
        assertThat(capturedClientIp.get()).isEqualTo(expected);
        assertThat(capturedClientIp.get()).doesNotContain("203", "113");
    }

    @Test
    @DisplayName("AC-MASK-002-3: 동일 IP는 동일 prefix를 생성하여 세션 추적성을 보장한다")
    void same_ip_yields_same_prefix() throws Exception {
        // given
        String ip = "172.16.0.99";
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        // 첫 번째 요청
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setRemoteAddr(ip);
        FilterChain chain1 = (r, s) -> first.set(MDC.get("clientIp"));
        filter.doFilter(req1, new MockHttpServletResponse(), chain1);

        // 두 번째 요청 (다른 traceId지만 동일 IP)
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setRemoteAddr(ip);
        FilterChain chain2 = (r, s) -> second.set(MDC.get("clientIp"));
        filter.doFilter(req2, new MockHttpServletResponse(), chain2);

        // then — 동일 IP → 동일 해시 prefix
        assertThat(first.get()).isEqualTo(second.get());
        assertThat(first.get()).hasSize(8);
    }

    @Test
    @DisplayName("AC-MASK-002-1: 빈 IP는 빈 문자열로 저장된다 (해시 충돌 방지)")
    void empty_ip_returns_empty() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(""); // 빈 IP
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedClientIp = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedClientIp.set(MDC.get("clientIp"));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(capturedClientIp.get()).isEqualTo("");
    }
}
