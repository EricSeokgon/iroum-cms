package kr.co.ircp.cms.domain.system.accesslog.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;
import kr.co.ircp.cms.domain.system.accesslog.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/**
 * 접속 로그 수집 필터.
 *
 * <p>REQ-SYSTEM-001-D — 모든 요청의 IP(SHA-256 익명화), user_agent, referrer,
 * page_url, status_code, response_time_ms를 기록한다.
 * 정적 리소스는 화이트리스트로 제외.
 */
// @MX:WARN: [AUTO] 정적 화이트리스트 패턴 — 경로 추가 시 반드시 여기서도 갱신
// @MX:REASON: 미갱신 시 정적 리소스 로그가 access_log에 쌓여 집계 왜곡 발생
@Component
@RequiredArgsConstructor
public class AccessLogFilter implements Filter {

    /** 정적 리소스 경로 화이트리스트 (접두사 또는 접미사 매칭) */
    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/static/", "/assets/", "/actuator/"
    );
    private static final Set<String> SKIP_SUFFIXES = Set.of(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".webp",
            ".ico", ".svg", ".woff", ".woff2", ".ttf", ".map"
    );
    private static final String SKIP_EXACT = "/favicon.ico";

    @Value("${iroum.access-log.ip-salt:changeme-ip-salt}")
    private String ipSalt;

    private final AccessLogService accessLogService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // 정적 리소스 제외
        if (shouldSkip(path)) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            int responseTimeMs = (int) (System.currentTimeMillis() - start);
            recordAsync(httpReq, httpRes.getStatus(), responseTimeMs);
        }
    }

    private boolean shouldSkip(String path) {
        if (SKIP_EXACT.equals(path)) return true;
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        String lower = path.toLowerCase();
        for (String suffix : SKIP_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        return false;
    }

    private void recordAsync(HttpServletRequest req, int status, int responseTimeMs) {
        try {
            String ipHash = hashIp(getClientIp(req), ipSalt);
            AccessLog log = AccessLog.builder()
                    .siteId(1L)
                    .ipHash(ipHash)
                    .userAgent(req.getHeader("User-Agent"))
                    .referrer(req.getHeader("Referer"))
                    .pageUrl(buildPageUrl(req))
                    .statusCode(status)
                    .responseTimeMs(responseTimeMs)
                    .createdAt(Instant.now())
                    .build();
            accessLogService.record(log);
        } catch (Exception ignored) {
            // 로그 수집 실패는 응답에 영향 없음
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String buildPageUrl(HttpServletRequest req) {
        String url = req.getRequestURI();
        String qs = req.getQueryString();
        return qs != null ? url + "?" + qs : url;
    }

    /**
     * SHA-256(ip + salt) → hex 64자 문자열.
     *
     * @param ip   클라이언트 IP
     * @param salt 환경변수 ACCESS_LOG_IP_SALT
     */
    static String hashIp(String ip, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((ip + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
