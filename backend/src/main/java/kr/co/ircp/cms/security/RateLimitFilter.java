package kr.co.ircp.cms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP 기반 단순 Rate Limiter (HIGH-7 보안 보강).
 *
 * <p>SPEC-CMS-SECURITY-HIGH-7 — 로그인 / OTP / 비밀번호 재설정 엔드포인트 brute-force 방어.
 *
 * <p>Bucket4j / Resilience4j 의존성 추가 없이 ConcurrentHashMap + AtomicInteger 로
 * 60초 슬라이딩 윈도우(reset 기반) 카운터를 유지한다.
 *
 * <p>경로별 분당 한도(기본):
 * <ul>
 *   <li>POST /api/v1/auth/login                — 10회/분</li>
 *   <li>POST /api/v1/auth/verify/request       —  5회/분</li>
 *   <li>POST /api/v1/auth/verify/confirm       —  5회/분</li>
 *   <li>POST /api/v1/auth/password/reset-request —  5회/분</li>
 *   <li>POST /api/v1/auth/password/reset-confirm —  5회/분</li>
 * </ul>
 *
 * <p>한도 초과 시 HTTP 429 Too Many Requests JSON 응답을 반환한다.
 *
 * <p>한계(설계 의도):
 * <ul>
 *   <li>단일 인스턴스 메모리 기반 — 다중 인스턴스 운영 시 Redis/Bucket4j 분산
 *       카운터로 격상 필요(SPEC TODO).</li>
 *   <li>X-Forwarded-For 첫번째 토큰을 클라이언트 IP로 사용 — 프록시 신뢰 환경 가정.</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] RateLimitFilter — 인증 계열 5개 엔드포인트 공통 brute-force 게이트
// @MX:REASON: SecurityFilterChain 이전 단계에서 모든 인증 요청을 검사 (fan_in >= 5)
// @MX:WARN: [AUTO] 메모리 기반 단일 인스턴스 카운터 — 다중 인스턴스 운영 시 분산화 필요
// @MX:REASON: ConcurrentHashMap 은 노드 간 공유되지 않아 분산 환경에서 한도 우회 가능
// @MX:SPEC: SPEC-CMS-SECURITY-HIGH-7
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** 카운터 윈도우(초). */
    private static final long WINDOW_SECONDS = 60L;

    /** 메모리 폭증 방지 상한 — 카운터 맵 키 수 초과 시 전체 reset 한 번 추가 수행. */
    private static final int MAX_TRACKED_KEYS = 100_000;

    @Value("${iroum.security.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${iroum.security.ratelimit.login-per-minute:10}")
    private int loginPerMinute;

    @Value("${iroum.security.ratelimit.otp-per-minute:5}")
    private int otpPerMinute;

    @Value("${iroum.security.ratelimit.password-reset-per-minute:5}")
    private int passwordResetPerMinute;

    /** key = method+":"+path+"|"+ip → 현재 윈도우 카운트. */
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService resetExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-reset");
        t.setDaemon(true);
        return t;
    });

    /**
     * Spring 빈 초기화 후 1분 주기 카운터 reset 스케줄 등록.
     */
    @jakarta.annotation.PostConstruct
    void scheduleReset() {
        resetExecutor.scheduleAtFixedRate(this::resetCounters,
                WINDOW_SECONDS, WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 빈 소멸 시 스케줄러 정상 종료.
     */
    @jakarta.annotation.PreDestroy
    void shutdown() {
        resetExecutor.shutdownNow();
    }

    private void resetCounters() {
        // 단순 clear — 윈도우 경계 정확도보다 메모리 안정성 우선.
        counters.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        int limit = limitFor(request);
        if (limit <= 0) {
            // 보호 대상 경로 아님 — 그대로 통과
            chain.doFilter(request, response);
            return;
        }

        // 메모리 보호: 키 수가 폭증한 경우 강제 reset
        if (counters.size() > MAX_TRACKED_KEYS) {
            log.warn("Rate-limit counter map exceeded {} keys — forced reset.", MAX_TRACKED_KEYS);
            counters.clear();
        }

        String clientIp = extractIp(request);
        String key = request.getMethod() + ":" + request.getRequestURI() + "|" + clientIp;
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger());
        int current = counter.incrementAndGet();

        if (current > limit) {
            log.warn("Rate-limit exceeded: ip={} path={} count={} limit={}",
                    clientIp, request.getRequestURI(), current, limit);
            // Jakarta servlet-api 6 에 SC_TOO_MANY_REQUESTS 상수가 없어 RFC 6585 코드 직접 사용.
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMIT_EXCEEDED\","
                            + "\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.\","
                            + "\"retryAfterSeconds\":" + WINDOW_SECONDS + "}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 요청 path / method 에 매칭되는 분당 허용 횟수 반환.
     *
     * @return 양수 = 적용 한도, 0 이하 = 미적용
     */
    private int limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return 0;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return 0;
        }
        if (path.equals("/api/v1/auth/login")) {
            return loginPerMinute;
        }
        if (path.equals("/api/v1/auth/verify/request")
                || path.equals("/api/v1/auth/verify/confirm")) {
            return otpPerMinute;
        }
        if (path.equals("/api/v1/auth/password/reset-request")
                || path.equals("/api/v1/auth/password/reset-confirm")) {
            return passwordResetPerMinute;
        }
        return 0;
    }

    /**
     * 클라이언트 IP 추출 — X-Forwarded-For 첫 토큰 우선.
     */
    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String ra = req.getRemoteAddr();
        return ra != null ? ra : "unknown";
    }
}
