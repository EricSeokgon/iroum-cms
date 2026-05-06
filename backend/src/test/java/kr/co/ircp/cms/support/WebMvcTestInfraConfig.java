package kr.co.ircp.cms.support;

import kr.co.ircp.cms.domain.system.accesslog.service.AccessLogService;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @WebMvcTest 슬라이스에서 필요한 공통 인프라 빈을 Mock으로 제공하는 테스트 구성.
 *
 * <p>{@link kr.co.ircp.cms.domain.system.accesslog.filter.AccessLogFilter}와
 * {@link kr.co.ircp.cms.domain.system.maintenance.filter.MaintenanceFilter}는
 * {@code @Component}로 선언되어 @WebMvcTest 컨텍스트에서도 로드된다.
 * 그러나 이 필터들이 의존하는 Service 빈은 슬라이스에 포함되지 않으므로
 * 각 컨트롤러 테스트에서 별도로 Mock을 제공해야 한다.
 *
 * <p>또한 {@code SecurityAutoConfiguration}을 제외한 컨트롤러 테스트에서도
 * {@code @AuthenticationPrincipal} 인자가 정상 해결되도록
 * {@link AuthenticationPrincipalArgumentResolver}를 직접 등록한다.
 *
 * <p>중복을 줄이기 위해 모든 @WebMvcTest 클래스는 다음과 같이 본 구성을 임포트한다.
 * <pre>{@code
 * @Import(WebMvcTestInfraConfig.class)
 * }</pre>
 */
// @MX:NOTE: [AUTO] @WebMvcTest 슬라이스 공통 Mock — 필터 의존성 해결용
@TestConfiguration
@EnableMethodSecurity
public class WebMvcTestInfraConfig {

    @MockBean
    private AccessLogService accessLogService;

    @MockBean
    private MaintenanceService maintenanceService;

    /**
     * SecurityAutoConfiguration이 제외된 슬라이스에서도
     * {@code @AuthenticationPrincipal} 어노테이션이 SecurityContext의 Principal을 해결할 수 있도록
     * {@link AuthenticationPrincipalArgumentResolver}를 직접 추가한다.
     */
    @Bean
    public WebMvcConfigurer authenticationPrincipalArgumentResolverConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new AuthenticationPrincipalArgumentResolver());
            }
        };
    }

    /**
     * 테스트 슬라이스용 Spring Security 필터 — {@code springSecurityFilterChain} 이름 필수.
     *
     * <p>{@code MockMvcSecurityConfiguration}이 {@code springSecurityFilterChain}
     * 빈을 조건으로 {@code SecurityMockMvcBuilderCustomizer}를 등록하므로,
     * {@code SecurityMockMvcRequestPostProcessors.authentication(...)} 동작을 보장하기 위해
     * 인증·인가 검사 없이 SecurityContext만 ThreadLocal에 전파하는 최소 필터 체인을 제공한다.
     *
     * <p>{@code MockMvcSecurityConfiguration}은 {@code springSecurityFilterChain} 빈이
     * {@link Filter} 타입이기를 기대하므로 {@link FilterChainProxy}로 감싼다.
     */
    @Bean(name = "springSecurityFilterChain")
    @ConditionalOnMissingBean(name = "springSecurityFilterChain")
    public Filter springSecurityFilterChain(SecurityFilterChain testSecurityFilterChain) {
        return new FilterChainProxy(testSecurityFilterChain);
    }

    /**
     * 테스트용 최소 {@link SecurityFilterChain} — Spring Security AutoConfig 백오프 트리거.
     *
     * <p>{@code SpringBootWebSecurityConfiguration}은 사용자가 정의한
     * {@link SecurityFilterChain} 빈이 없을 때만 {@code defaultSecurityFilterChain}를 만들고,
     * 그 빈은 {@code HttpSecurity} 의존성을 요구한다. 슬라이스 테스트에서
     * {@code HttpSecurity}를 제공하지 않으므로 직접 {@link DefaultSecurityFilterChain}를 등록해
     * 자동 설정을 우회한다.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain testSecurityFilterChain() {
        SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        SecurityContextHolderFilter contextFilter = new SecurityContextHolderFilter(repository);
        return new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, contextFilter);
    }
}
