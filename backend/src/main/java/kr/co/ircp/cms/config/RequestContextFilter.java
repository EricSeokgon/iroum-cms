package kr.co.ircp.cms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 컨텍스트 정보를 MDC에 적재하는 필터.
 *
 * <p>JwtAuthenticationFilter보다 먼저 실행되어야 감사 로그에서
 * ipAddress / userAgent / traceId를 올바르게 참조할 수 있다.
 *
 * <p>SPEC-CMS-005 §7 AuditLogAspect가 MDC에서 해당 값을 읽는다.
 */
// @MX:WARN: [AUTO] MDC는 ThreadLocal 기반 — finally에서 반드시 clear() 필요
// @MX:REASON: MDC.clear() 누락 시 스레드 풀 재사용 환경에서 이전 요청 데이터가 누출됨
@Component("cmsRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final String MDC_IP = "ipAddress";
    private static final String MDC_UA = "userAgent";
    private static final String MDC_TRACE = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try {
            // SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002 — ipAddress SHA-256 prefix 8 chars
            // AuditLogAspect가 MDC.ipAddress를 평문 IP 컬럼이 아닌 마스킹된 식별자로 기록하게 한다.
            MDC.put(MDC_IP, hashIp(extractClientIp(request)));
            MDC.put(MDC_UA, nullToEmpty(request.getHeader("User-Agent")));
            MDC.put(MDC_TRACE, generateTraceId());
            chain.doFilter(request, response);
        } finally {
            // 반드시 clear — 스레드 재사용 시 이전 요청 데이터 누출 방지
            MDC.remove(MDC_IP);
            MDC.remove(MDC_UA);
            MDC.remove(MDC_TRACE);
        }
    }

    /**
     * X-Forwarded-For 우선, fallback은 RemoteAddr.
     * 프록시/로드 밸런서 환경에서 실제 클라이언트 IP를 추출한다.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 다중 프록시 경유 시 첫 번째 주소가 원본 IP
            return xff.split(",")[0].strip();
        }
        return nullToEmpty(request.getRemoteAddr());
    }

    /** 16자 traceId (UUID 변형). */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 클라이언트 IP를 SHA-256 hex prefix(8 chars)로 마스킹한다.
     *
     * <p>SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002 — 평문 IP 노출 차단.
     * 빈 문자열은 그대로 빈 문자열을 반환한다(추적성 손실 허용).
     */
    // @MX:NOTE: [AUTO] hashIp — ipAddress SHA-256 prefix 마스킹
    // @MX:SPEC: SPEC-CMS-SECURITY-PII-MASKING-001 / REQ-PII-MASK-002
    private String hashIp(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return "";
        }
        return HashUtil.sha256Hex(clientIp).substring(0, 8);
    }

    private String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
