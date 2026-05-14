package kr.co.ircp.cms.config;

import kr.co.ircp.cms.common.log.MdcLoggingFilter;
import kr.co.ircp.cms.domain.system.accesslog.filter.AccessLogFilter;
import kr.co.ircp.cms.domain.system.maintenance.filter.MaintenanceFilter;
import kr.co.ircp.cms.security.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet Filter 등록 순서 설정.
 *
 * <p>REQ-SYSTEM-001-D / REQ-SYSTEM-005-D
 * 두 필터는 @Component로 자동 등록되지만, 실행 순서를 명시적으로 제어한다.
 *
 * <ul>
 *   <li>Order 10 — AccessLogFilter: 모든 요청 앞에서 시작 시각 기록</li>
 *   <li>Order 20 — MaintenanceFilter: JWT 인증 이후 역할 확인이 필요하므로 뒤에 위치</li>
 * </ul>
 *
 * MaintenanceFilter는 SecurityFilterChain(Spring Security) 외부의 Servlet 레벨 필터이므로
 * JwtAuthenticationFilter가 SecurityFilterChain 내에서 먼저 실행된다는 보장이 없다.
 * 따라서 order 를 Security 기본값(Integer.MAX_VALUE - 5) 보다 크게 설정하여
 * SecurityFilterChain 이후에 실행되도록 한다.
 */
@Configuration
public class FilterRegistrationConfig {

    // @MX:NOTE: [AUTO] FilterRegistrationBean 순서
    // Order 5  — MdcLoggingFilter: 모든 필터 최우선, MDC를 주입하여 이후 로그에 traceId 포함
    // Order 10 — AccessLogFilter: MDC 이후 시작 시각 기록
    // Order 20 — MaintenanceFilter: Security 이후 역할 확인
    // Security FilterChain 기본 order = Integer.MAX_VALUE - 5; Servlet Filter order가 더 낮으면 먼저 실행

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration(
            MdcLoggingFilter filter) {
        FilterRegistrationBean<MdcLoggingFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(5);  // AccessLogFilter(10)보다 먼저 실행 — traceId 주입 최우선
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(
            AccessLogFilter filter) {
        FilterRegistrationBean<AccessLogFilter> reg = new FilterRegistrationBean<>(filter);
        // Security FilterChain 보다 먼저 실행 (order 낮을수록 먼저)
        // 접속 로그는 Security 처리 전/후 상관없이 응답 시간을 측정해야 하므로 최우선
        reg.setOrder(10);
        reg.addUrlPatterns("/*");
        return reg;
    }

    /**
     * HIGH-7 — 로그인 / OTP / 비밀번호 재설정 brute-force 방어 Rate Limiter.
     *
     * <p>order=15 — AccessLogFilter(10) 직후, Security FilterChain(MAX_VALUE-5) 보다 먼저 실행.
     * 한도 초과 요청은 인증 처리에 도달하기 전에 429 로 차단된다.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(15);
        reg.addUrlPatterns("/api/v1/auth/*");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<MaintenanceFilter> maintenanceFilterRegistration(
            MaintenanceFilter filter) {
        FilterRegistrationBean<MaintenanceFilter> reg = new FilterRegistrationBean<>(filter);
        // Security FilterChain(기본 order = MAX_VALUE - 5 ≈ 2147483642) 이후에 실행
        // JWT 필터가 SecurityContextHolder 채운 뒤 역할 확인 가능
        reg.setOrder(Integer.MAX_VALUE - 10);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
