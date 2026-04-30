package kr.co.ircp.cms.config;

import kr.co.ircp.cms.domain.system.accesslog.filter.AccessLogFilter;
import kr.co.ircp.cms.domain.system.maintenance.filter.MaintenanceFilter;
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

    // @MX:NOTE: [AUTO] FilterRegistrationBean 순서 — AccessLog(10) < Maintenance(20) < Security(기본)
    // Security FilterChain 기본 order = Integer.MAX_VALUE - 5; Servlet Filter order가 더 낮으면 먼저 실행
    // 큰 숫자 = 늦게 실행. Security보다 높은 order 값을 주면 Security 이후에 동작

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
