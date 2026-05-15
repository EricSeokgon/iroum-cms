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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * IP 기반 Rate Limiter — 키당 독립 윈도우 + 신뢰 프록시 XFF 검증 (HIGH-7, WARN-3).
 *
 * <p>SPEC-CMS-SECURITY-HIGH-7 — 로그인 / OTP / 비밀번호 재설정 엔드포인트 brute-force 방어.
 *
 * <p>각 IP+경로 키가 독립 {@link WindowCounter}를 유지하므로 전역 리셋 기반
 * 고정 윈도우의 경계 취약점(경계 직전+직후 2×한도 요청)을 제거한다.
 *
 * <p>X-Forwarded-For 헤더는 신뢰 프록시(RFC-1918 사설 대역 또는
 * {@code iroum.security.ratelimit.trusted-proxies} 설정값)에서 수신된 요청에서만 사용한다.
 * 신뢰하지 않는 공인 IP 에서 온 XFF 는 무시되어 IP 위장 차단 우회 공격을 방어한다.
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
 * <p>한계(설계 의도):
 * <ul>
 *   <li>단일 인스턴스 메모리 기반 — 다중 인스턴스 운영 시 Redis/Bucket4j 분산
 *       카운터로 격상 필요(SPEC TODO).</li>
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

    private static final long WINDOW_SECONDS = 60L;
    private static final int MAX_TRACKED_KEYS = 100_000;

    @Value("${iroum.security.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${iroum.security.ratelimit.login-per-minute:10}")
    private int loginPerMinute;

    @Value("${iroum.security.ratelimit.otp-per-minute:5}")
    private int otpPerMinute;

    @Value("${iroum.security.ratelimit.password-reset-per-minute:5}")
    private int passwordResetPerMinute;

    /**
     * 신뢰 프록시 IP 목록 (콤마 구분).
     * {@code PRIVATE} 토큰 포함 시 RFC-1918 사설 대역 전체를 신뢰한다(기본값).
     * X-Forwarded-For 헤더는 이 목록에 속한 remoteAddr 에서만 신뢰된다.
     */
    @Value("${iroum.security.ratelimit.trusted-proxies:127.0.0.1,::1,PRIVATE}")
    private String trustedProxiesConfig;

    /** key = method+":"+path+"|"+ip → 키당 독립 윈도우 카운터. */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    private Set<String> trustedProxyExact = Set.of();
    private boolean trustPrivateRanges = true; // @PostConstruct 미실행 환경(테스트)의 기본값

    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    @jakarta.annotation.PostConstruct
    void init() {
        parseTrustedProxies();
        // 만료 키 정리 — 전역 clear 대신 stale 항목만 제거
        cleanupExecutor.scheduleAtFixedRate(this::cleanupStaleCounters,
                WINDOW_SECONDS * 2, WINDOW_SECONDS * 2, TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        cleanupExecutor.shutdownNow();
    }

    private void parseTrustedProxies() {
        Set<String> tokens = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.HashSet::new));
        trustPrivateRanges = tokens.remove("PRIVATE");
        trustedProxyExact = Set.copyOf(tokens);
    }

    /** 만료된 카운터 키만 제거한다. 전역 clear 와 달리 활성 윈도우를 보존한다. */
    void cleanupStaleCounters() {
        counters.entrySet().removeIf(e -> e.getValue().isStale(WINDOW_SECONDS));
    }

    /** 테스트 및 강제 초기화용 — 모든 카운터를 즉시 제거한다. */
    void resetCounters() {
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
            chain.doFilter(request, response);
            return;
        }

        if (counters.size() > MAX_TRACKED_KEYS) {
            log.warn("Rate-limit counter map exceeded {} keys — forced cleanup.", MAX_TRACKED_KEYS);
            cleanupStaleCounters();
        }

        String clientIp = extractIp(request);
        String key = request.getMethod() + ":" + request.getRequestURI() + "|" + clientIp;
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        int current = counter.incrementAndGet(WINDOW_SECONDS);

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

    private int limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return 0;
        String path = request.getRequestURI();
        if (path == null) return 0;
        if (path.equals("/api/v1/auth/login")) return loginPerMinute;
        if (path.equals("/api/v1/auth/verify/request")
                || path.equals("/api/v1/auth/verify/confirm")) return otpPerMinute;
        if (path.equals("/api/v1/auth/password/reset-request")
                || path.equals("/api/v1/auth/password/reset-confirm")) return passwordResetPerMinute;
        return 0;
    }

    /**
     * 클라이언트 IP 추출.
     * X-Forwarded-For 헤더는 신뢰 프록시({@link #isTrustedProxy})에서만 사용한다.
     * 신뢰하지 않는 외부 IP 에서 온 XFF 헤더는 무시하여 IP 위장 차단 우회를 방어한다.
     */
    private String extractIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && isTrustedProxy(remoteAddr)) {
            return xff.split(",")[0].trim();
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * remoteAddr 가 신뢰 프록시인지 검사한다.
     * {@code PRIVATE} 설정 시 RFC-1918 사설 대역 전체(127.x, 10.x, 172.16-31.x, 192.168.x)를 신뢰한다.
     */
    boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        if (trustedProxyExact.contains(addr)) return true;
        if (!trustPrivateRanges) return false;
        if (addr.startsWith("127.") || addr.equals("::1")) return true;
        if (addr.startsWith("10.") || addr.startsWith("192.168.")) return true;
        if (addr.startsWith("172.")) {
            try {
                int second = Integer.parseInt(addr.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) { return false; }
        }
        return false;
    }

    /**
     * 키당 독립 고정 윈도우 카운터.
     *
     * <p>전역 reset 이 아닌 첫 요청 시점 기준으로 60초 윈도우를 유지한다.
     * 전역 리셋 경계(t=0, t=60)에서 2×한도 공격이 가능하던 취약점을 제거한다.
     */
    static final class WindowCounter {
        private long windowStart = 0L;
        private int count = 0;

        /** 현재 윈도우 카운트를 증가하고 반환한다. 윈도우 만료 시 새 윈도우를 시작한다. */
        synchronized int incrementAndGet(long windowSeconds) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowSeconds * 1_000L) {
                windowStart = now;
                count = 0;
            }
            return ++count;
        }

        /** 마지막 활동 이후 2×윈도우가 지난 경우 정리 대상으로 판정한다. */
        synchronized boolean isStale(long windowSeconds) {
            return System.currentTimeMillis() - windowStart >= 2L * windowSeconds * 1_000L;
        }
    }
}
