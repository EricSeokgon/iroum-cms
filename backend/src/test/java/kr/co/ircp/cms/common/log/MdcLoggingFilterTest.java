package kr.co.ircp.cms.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * MdcLoggingFilter 단위 테스트 — REQ-CROSS-007-D-2.
 *
 * <p>FilterChain을 Mockito mock으로 생성하여 MDC 주입/clear 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MdcLoggingFilter 단위 테스트")
class MdcLoggingFilterTest {

    @InjectMocks
    private MdcLoggingFilter filter;

    @AfterEach
    void cleanup() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldInjectMdcFieldsOnRequest — 요청 진입 시 traceId, requestId, clientIp가 MDC에 주입된다")
    void shouldInjectMdcFieldsOnRequest() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // MDC 값을 FilterChain 내부에서 캡처
        AtomicReference<String> capturedTraceId   = new AtomicReference<>();
        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedClientIp  = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            capturedTraceId.set(MDC.get("traceId"));
            capturedRequestId.set(MDC.get("requestId"));
            capturedClientIp.set(MDC.get("clientIp"));
        };

        // when
        filter.doFilter(request, response, chain);

        // then — 필터 체인 실행 중에 MDC 값이 존재했음을 검증
        assertThat(capturedTraceId.get())
                .as("traceId는 UUID 형식이어야 한다")
                .isNotNull()
                .matches("[0-9a-f\\-]{36}");
        assertThat(capturedRequestId.get())
                .as("requestId는 UUID 형식이어야 한다")
                .isNotNull()
                .matches("[0-9a-f\\-]{36}");
        // SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002:
        //   clientIp는 SHA-256 hex prefix(8 chars)로 마스킹된다 (PII 보호 + 추적성 양립)
        assertThat(capturedClientIp.get())
                .as("clientIp는 remoteAddr의 SHA-256 hex prefix(8 chars)이어야 한다")
                .isEqualTo(HashUtil.sha256Hex("10.0.0.1").substring(0, 8));

        // 필터 완료 후 MDC가 clear 되었는지 확인
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("shouldClearMdcOnException — 체인에서 예외가 발생해도 MDC가 clear 된다")
    void shouldClearMdcOnException() {
        // given
        MockHttpServletRequest request   = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("의도적 테스트 예외");
        };

        // when — 예외가 전파되어야 함
        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("의도적 테스트 예외");

        // then — 예외 발생 후에도 MDC가 clear 되어야 함
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("clientIp")).isNull();
    }

    @Test
    @DisplayName("shouldUseExistingTraceIdFromHeader — X-B3-TraceId 헤더가 있으면 새 UUID 대신 재사용한다")
    void shouldUseExistingTraceIdFromHeader() throws Exception {
        // given
        String existingTraceId = "aabbccdd-1234-5678-abcd-ef0123456789";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-B3-TraceId", existingTraceId);
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedTraceId = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedTraceId.set(MDC.get("traceId"));

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(capturedTraceId.get())
                .as("X-B3-TraceId 헤더 값이 traceId로 사용되어야 한다")
                .isEqualTo(existingTraceId);
    }
}
