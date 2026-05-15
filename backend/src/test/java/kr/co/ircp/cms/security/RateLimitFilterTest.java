package kr.co.ircp.cms.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RateLimitFilter 단위 테스트 (SPEC-CMS-SECURITY-HIGH-7).
 *
 * <p>실제 Spring 컨텍스트 없이 ReflectionTestUtils 로 {@code @Value} 필드를 직접
 * 주입하고, MockHttpServletRequest/Response 로 필터 분기 시나리오를 검증한다.
 *
 * <p>커버 분기:
 * <ul>
 *   <li>비활성 모드 — 즉시 통과</li>
 *   <li>보호 대상 외 경로 (GET / 비-인증 POST) — 즉시 통과</li>
 *   <li>한도 미만 / 경계 / 초과 시나리오</li>
 *   <li>IP 별 독립 카운터, X-Forwarded-For 우선순위</li>
 *   <li>윈도우 리셋 후 재허용</li>
 *   <li>로그인 / OTP / 비밀번호 재설정 5개 엔드포인트별 한도 적용</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 단위 테스트 (SPEC-CMS-SECURITY-HIGH-7)")
class RateLimitFilterTest {

    // 테스트 한도 — 운영 기본값 대비 작은 값으로 시나리오 단순화
    private static final int LOGIN_LIMIT = 3;
    private static final int OTP_LIMIT = 2;
    private static final int PASSWORD_RESET_LIMIT = 2;

    @Mock
    private FilterChain chain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // @Value 필드 — Spring DI 없이 직접 주입
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "loginPerMinute", LOGIN_LIMIT);
        ReflectionTestUtils.setField(filter, "otpPerMinute", OTP_LIMIT);
        ReflectionTestUtils.setField(filter, "passwordResetPerMinute", PASSWORD_RESET_LIMIT);
        // @PostConstruct scheduleReset() 은 단위 테스트에서 호출하지 않음
        // → 1분 스케줄러 미동작 → 시간 윈도우 리셋은 resetCounters() 직접 호출로 시뮬레이션
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest loginPost(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    private MockHttpServletRequest postRequest(String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @SuppressWarnings("unchecked")
    private Map<String, AtomicInteger> internalCounters() {
        return (Map<String, AtomicInteger>) ReflectionTestUtils.getField(filter, "counters");
    }

    /** 윈도우 리셋 시뮬레이션 — 운영의 1분 스케줄러 대신 사용. */
    private void simulateWindowReset() {
        ReflectionTestUtils.invokeMethod(filter, "resetCounters");
    }

    // ─── 비활성 / 미적용 시나리오 ──────────────────────────────────────────

    @Nested
    @DisplayName("필터 비활성 / 보호 대상 외 경로")
    class PassThroughCases {

        @Test
        @DisplayName("enabled=false 면 한도 검사 없이 즉시 다음 필터로 통과")
        void disabledFilter_passesAlways() throws Exception {
            ReflectionTestUtils.setField(filter, "enabled", false);

            // 한도(3)를 초과하는 요청 5건이어도 모두 통과해야 함
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = loginPost("10.0.0.1");
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);
                assertThat(res.getStatus()).isEqualTo(200);
            }
            verify(chain, times(5)).doFilter(any(), any());
            assertThat(internalCounters()).isEmpty();
        }

        @Test
        @DisplayName("GET 메서드는 보호 대상 아님 — 통과")
        void getMethod_isNotRateLimited() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(internalCounters()).isEmpty();
        }

        @Test
        @DisplayName("매칭되지 않는 POST 경로 (예: /api/v1/posts) — 통과 + 카운터 미증가")
        void unprotectedPostPath_isNotRateLimited() throws Exception {
            MockHttpServletRequest req = postRequest("/api/v1/posts", "10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();

            // 한도(3)보다 많은 10건의 비보호 POST 도 모두 통과
            for (int i = 0; i < 10; i++) {
                MockHttpServletResponse r = new MockHttpServletResponse();
                filter.doFilterInternal(req, r, chain);
                assertThat(r.getStatus()).isEqualTo(200);
            }
            verify(chain, times(10)).doFilter(any(), any());
            assertThat(internalCounters()).isEmpty();
        }
    }

    // ─── 정상 / 경계 / 초과 시나리오 ─────────────────────────────────────

    @Nested
    @DisplayName("로그인 엔드포인트 한도 분기")
    class LoginLimitCases {

        @Test
        @DisplayName("한도 미만 요청 → 통과")
        void underLimit_passes() throws Exception {
            MockHttpServletRequest req = loginPost("192.168.0.10");
            MockHttpServletResponse res = new MockHttpServletResponse();

            filter.doFilterInternal(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("한도와 정확히 같은 요청 수 → 마지막 요청도 통과 (경계)")
        void atExactLimit_stillPasses() throws Exception {
            for (int i = 1; i <= LOGIN_LIMIT; i++) {
                MockHttpServletRequest req = loginPost("192.168.0.20");
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);
                assertThat(res.getStatus())
                        .as("요청 %d/%d 통과", i, LOGIN_LIMIT)
                        .isEqualTo(200);
            }
            verify(chain, times(LOGIN_LIMIT)).doFilter(any(), any());
        }

        @Test
        @DisplayName("한도 초과 요청 → 429 + RATE_LIMIT_EXCEEDED + Retry-After 60")
        void overLimit_returns429() throws Exception {
            // LOGIN_LIMIT 만큼 정상 통과
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                filter.doFilterInternal(loginPost("192.168.0.30"),
                        new MockHttpServletResponse(), chain);
            }

            // LOGIN_LIMIT + 1 번째 요청 → 차단
            MockHttpServletRequest blocked = loginPost("192.168.0.30");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(blocked, res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getContentType()).isEqualTo("application/json;charset=UTF-8");
            assertThat(res.getHeader("Retry-After")).isEqualTo("60");
            assertThat(res.getContentAsString())
                    .contains("RATE_LIMIT_EXCEEDED")
                    .contains("\"retryAfterSeconds\":60");
            // 차단된 요청은 다음 필터로 전달되지 않음
            verify(chain, times(LOGIN_LIMIT)).doFilter(any(), any());
        }

        @Test
        @DisplayName("한도 초과 후에도 추가 요청은 계속 429 차단")
        void continuesToBlock_afterFirstReject() throws Exception {
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                filter.doFilterInternal(loginPost("192.168.0.40"),
                        new MockHttpServletResponse(), chain);
            }
            // 추가 차단 요청 3건
            for (int i = 0; i < 3; i++) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(loginPost("192.168.0.40"), res, chain);
                assertThat(res.getStatus()).isEqualTo(429);
            }
            verify(chain, times(LOGIN_LIMIT)).doFilter(any(), any());
        }
    }

    // ─── IP 격리 / X-Forwarded-For ───────────────────────────────────────

    @Nested
    @DisplayName("IP 별 독립 카운터 / X-Forwarded-For 해석")
    class IpScopingCases {

        @Test
        @DisplayName("서로 다른 IP 는 독립 카운터를 사용한다")
        void differentIps_haveIndependentCounters() throws Exception {
            // IP A: 한도 가득 채움
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                filter.doFilterInternal(loginPost("10.0.0.1"),
                        new MockHttpServletResponse(), chain);
            }
            // IP A 1건 추가 → 차단
            MockHttpServletResponse blockedA = new MockHttpServletResponse();
            filter.doFilterInternal(loginPost("10.0.0.1"), blockedA, chain);
            assertThat(blockedA.getStatus()).isEqualTo(429);

            // IP B: 첫 요청은 차단 영향을 받지 않아야 함
            MockHttpServletResponse passB = new MockHttpServletResponse();
            filter.doFilterInternal(loginPost("10.0.0.2"), passB, chain);
            assertThat(passB.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 첫 토큰을 클라이언트 IP 로 사용")
        void xForwardedFor_takesPrecedenceOverRemoteAddr() throws Exception {
            // 동일 remoteAddr 이지만 X-Forwarded-For 가 다른 IP 두 그룹
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                MockHttpServletRequest req = loginPost("10.0.0.99");
                req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.99");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }

            // 같은 remoteAddr 이라도 X-Forwarded-For 가 다르면 별도 카운터
            MockHttpServletRequest reqOther = loginPost("10.0.0.99");
            reqOther.addHeader("X-Forwarded-For", "203.0.113.2, 10.0.0.99");
            MockHttpServletResponse resOther = new MockHttpServletResponse();
            filter.doFilterInternal(reqOther, resOther, chain);
            assertThat(resOther.getStatus()).isEqualTo(200);

            // 첫 IP 는 한도 초과 시 차단됨
            MockHttpServletRequest reqBlocked = loginPost("10.0.0.99");
            reqBlocked.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.99");
            MockHttpServletResponse resBlocked = new MockHttpServletResponse();
            filter.doFilterInternal(reqBlocked, resBlocked, chain);
            assertThat(resBlocked.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("빈 X-Forwarded-For 헤더는 무시하고 remoteAddr 사용")
        void blankXForwardedFor_fallsBackToRemoteAddr() throws Exception {
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                MockHttpServletRequest req = loginPost("10.0.0.50");
                req.addHeader("X-Forwarded-For", "   ");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }
            // remoteAddr 기준 한도 초과
            MockHttpServletRequest blocked = loginPost("10.0.0.50");
            blocked.addHeader("X-Forwarded-For", "   ");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(blocked, res, chain);
            assertThat(res.getStatus()).isEqualTo(429);
        }
    }

    // ─── 시간 윈도우 리셋 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("시간 윈도우 리셋")
    class WindowResetCases {

        @Test
        @DisplayName("리셋 후 카운터가 초기화되어 같은 IP 가 다시 통과")
        void afterReset_counterClears_and_requestPasses() throws Exception {
            // 한도 가득
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                filter.doFilterInternal(loginPost("172.16.0.1"),
                        new MockHttpServletResponse(), chain);
            }
            // 초과 → 차단 확인
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilterInternal(loginPost("172.16.0.1"), blocked, chain);
            assertThat(blocked.getStatus()).isEqualTo(429);

            // 윈도우 리셋 시뮬레이션
            simulateWindowReset();
            assertThat(internalCounters()).isEmpty();

            // 리셋 직후 요청은 다시 통과
            MockHttpServletResponse afterReset = new MockHttpServletResponse();
            filter.doFilterInternal(loginPost("172.16.0.1"), afterReset, chain);
            assertThat(afterReset.getStatus()).isEqualTo(200);
        }
    }

    // ─── 엔드포인트별 한도 적용 ──────────────────────────────────────────

    @Nested
    @DisplayName("로그인/OTP/비밀번호 재설정 5개 엔드포인트별 한도")
    class PerEndpointLimitCases {

        @Test
        @DisplayName("OTP 요청 엔드포인트 — otpPerMinute 한도 적용")
        void otpRequestEndpoint_appliesOtpLimit() throws Exception {
            String ip = "10.1.0.1";
            // OTP_LIMIT 만큼 통과
            for (int i = 0; i < OTP_LIMIT; i++) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(
                        postRequest("/api/v1/auth/verify/request", ip), res, chain);
                assertThat(res.getStatus()).isEqualTo(200);
            }
            // 초과 → 429
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilterInternal(
                    postRequest("/api/v1/auth/verify/request", ip), blocked, chain);
            assertThat(blocked.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("OTP 확인 엔드포인트 — otpPerMinute 한도 적용")
        void otpConfirmEndpoint_appliesOtpLimit() throws Exception {
            String ip = "10.1.0.2";
            for (int i = 0; i < OTP_LIMIT; i++) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(
                        postRequest("/api/v1/auth/verify/confirm", ip), res, chain);
                assertThat(res.getStatus()).isEqualTo(200);
            }
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilterInternal(
                    postRequest("/api/v1/auth/verify/confirm", ip), blocked, chain);
            assertThat(blocked.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("비밀번호 재설정 요청 — passwordResetPerMinute 한도 적용")
        void passwordResetRequestEndpoint_appliesPasswordResetLimit() throws Exception {
            String ip = "10.1.0.3";
            for (int i = 0; i < PASSWORD_RESET_LIMIT; i++) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(
                        postRequest("/api/v1/auth/password/reset-request", ip), res, chain);
                assertThat(res.getStatus()).isEqualTo(200);
            }
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilterInternal(
                    postRequest("/api/v1/auth/password/reset-request", ip), blocked, chain);
            assertThat(blocked.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("비밀번호 재설정 확인 — passwordResetPerMinute 한도 적용")
        void passwordResetConfirmEndpoint_appliesPasswordResetLimit() throws Exception {
            String ip = "10.1.0.4";
            for (int i = 0; i < PASSWORD_RESET_LIMIT; i++) {
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(
                        postRequest("/api/v1/auth/password/reset-confirm", ip), res, chain);
                assertThat(res.getStatus()).isEqualTo(200);
            }
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            filter.doFilterInternal(
                    postRequest("/api/v1/auth/password/reset-confirm", ip), blocked, chain);
            assertThat(blocked.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("로그인 / OTP / 비밀번호 카운터는 서로 격리되어 영향 없음")
        void differentEndpoints_haveIndependentCounters() throws Exception {
            String ip = "10.1.0.5";
            // 로그인 한도 가득
            for (int i = 0; i < LOGIN_LIMIT; i++) {
                filter.doFilterInternal(loginPost(ip), new MockHttpServletResponse(), chain);
            }
            MockHttpServletResponse blockedLogin = new MockHttpServletResponse();
            filter.doFilterInternal(loginPost(ip), blockedLogin, chain);
            assertThat(blockedLogin.getStatus()).isEqualTo(429);

            // OTP 는 별개 카운터 — 첫 요청 통과해야 함
            MockHttpServletResponse otpFirst = new MockHttpServletResponse();
            filter.doFilterInternal(
                    postRequest("/api/v1/auth/verify/request", ip), otpFirst, chain);
            assertThat(otpFirst.getStatus()).isEqualTo(200);
        }
    }

    // ─── null URI 방어 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("null/edge URI 분기")
    class EdgeCases {

        @Test
        @DisplayName("POST 이지만 보호 경로가 아닌 비슷한 URI — 통과")
        void similarButUnprotectedPath_passes() throws Exception {
            // /api/v1/auth/login 과 매우 유사하지만 startsWith 가 아닌 정확 매칭이므로 통과
            MockHttpServletRequest req =
                    postRequest("/api/v1/auth/login/extra", "10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);

            verify(chain).doFilter(req, res);
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(internalCounters()).isEmpty();
        }
    }

    /**
     * Mockito any() helper - import 정리를 위해 로컬 정의.
     */
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
