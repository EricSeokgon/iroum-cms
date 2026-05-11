package kr.co.ircp.cms.common.log;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC 로깅 필터 — REQ-CROSS-007-D-2.
 *
 * <p>진입 시 traceId, requestId, userId, clientIp를 MDC에 주입하고
 * 응답 후 MDC를 clear하여 스레드 풀 재사용 시 오염을 방지한다.
 *
 * <p>traceId 우선순위: X-B3-TraceId 헤더 > X-Trace-Id 헤더 > UUID 자동 발급.
 */
// @MX:ANCHOR: [AUTO] MdcLoggingFilter.doFilter — MDC 주입/clear 진입점
// @MX:REASON: FilterRegistrationConfig, 로그 파이프라인, 테스트가 참조 (fan_in >= 3)
// @MX:WARN: [AUTO] MDC.clear() — try-finally 보장 필수
// @MX:REASON: 스레드 풀 재사용 환경에서 clear 누락 시 이전 요청 컨텍스트가 오염됨
@Component
public class MdcLoggingFilter implements Filter {

    /** B3 trace header (Zipkin/Brave 호환). */
    private static final String HEADER_B3_TRACE = "X-B3-TraceId";
    /** 자체 trace header 폴백. */
    private static final String HEADER_TRACE    = "X-Trace-Id";

    private static final String MDC_TRACE_ID    = "traceId";
    private static final String MDC_SPAN_ID     = "spanId";
    private static final String MDC_USER_ID     = "userId";
    private static final String MDC_REQUEST_ID  = "requestId";
    private static final String MDC_CLIENT_IP   = "clientIp";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            HttpServletRequest httpReq = (HttpServletRequest) request;

            // traceId: B3 헤더 > X-Trace-Id > UUID
            String traceId = httpReq.getHeader(HEADER_B3_TRACE);
            if (traceId == null || traceId.isBlank()) {
                traceId = httpReq.getHeader(HEADER_TRACE);
            }
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }

            MDC.put(MDC_TRACE_ID,   traceId);
            MDC.put(MDC_SPAN_ID,    UUID.randomUUID().toString());
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());
            // SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002 — clientIp SHA-256 prefix 8 chars
            // 평문 IP 대신 단방향 해시 prefix만 MDC에 저장하여 동일 사용자 추적성은 유지하되 PII 노출을 차단한다.
            MDC.put(MDC_CLIENT_IP,  hashClientIp(resolveClientIp(httpReq)));

            // userId: SecurityContext에서 추출 (인증 이전 필터 실행 시 null)
            String userId = resolveUserId();
            if (userId != null) {
                MDC.put(MDC_USER_ID, userId);
            }

            chain.doFilter(request, response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * X-Forwarded-For 헤더를 우선하여 클라이언트 IP를 반환한다.
     *
     * <p>프록시 뒤에서 실행될 때 remoteAddr은 프록시 IP가 된다.
     */
    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // "client, proxy1, proxy2" 형식에서 첫 번째 값
            int idx = xff.indexOf(',');
            return idx >= 0 ? xff.substring(0, idx).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }

    /**
     * 클라이언트 IP를 SHA-256 hex prefix(8 chars)로 마스킹한다.
     *
     * <p>SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-002 — 평문 IP 노출 차단.
     * 동일 IP는 동일 prefix로 매핑되어 세션 단위 추적성은 유지된다.
     *
     * @param clientIp 평문 IP(null/빈 문자열 허용)
     * @return SHA-256 hex prefix 8자 또는 빈 문자열
     */
    // @MX:NOTE: [AUTO] hashClientIp — clientIp SHA-256 prefix 마스킹
    // @MX:SPEC: SPEC-CMS-SECURITY-PII-MASKING-001 / REQ-PII-MASK-002
    private String hashClientIp(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return "";
        }
        return HashUtil.sha256Hex(clientIp).substring(0, 8);
    }

    /**
     * SecurityContext에서 인증된 사용자 ID를 추출한다.
     *
     * <p>인증 전 필터 단계이거나 익명 요청이면 null을 반환한다.
     */
    private String resolveUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtPrincipal p) {
                return String.valueOf(p.userId());
            }
        } catch (Exception ignored) {
            // SecurityContext 미초기화 상황에서 안전하게 무시
        }
        return null;
    }
}
