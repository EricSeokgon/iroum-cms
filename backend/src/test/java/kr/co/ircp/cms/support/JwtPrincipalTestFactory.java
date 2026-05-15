package kr.co.ircp.cms.support;

import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;

/**
 * @WebMvcTest 슬라이스용 JwtPrincipal 기반 인증 토큰 헬퍼.
 *
 * <p>운영 코드는 @AuthenticationPrincipal(expression = "userId") Long userId 또는
 * @AuthenticationPrincipal JwtPrincipal principal 인자를 통해 사용자 식별 정보를 추출한다.
 * Spring Security 표준 @WithMockUser 는 {@code User} 객체를 Principal 로 설정하므로
 * SpEL 'userId' 평가가 실패한다(EL1008E).
 *
 * <p>본 팩토리는 슬라이스 테스트에서 @WithMockUser 대신 사용할 수 있는 JwtPrincipal 인스턴스와
 * MockMvc {@code .with(...)} 호출용 RequestPostProcessor 를 제공한다.
 *
 * <p>사용 예:
 * <pre>{@code
 * mockMvc.perform(post("/api/v1/dashboard/layouts")
 *         .with(JwtPrincipalTestFactory.jwtAuth(
 *             JwtPrincipalTestFactory.testPrincipal())))
 *     .andExpect(status().isOk());
 * }</pre>
 */
// @MX:NOTE: [AUTO] JwtPrincipal 슬라이스 테스트 헬퍼 — @AuthenticationPrincipal(expression="userId") SpEL 호환성
public final class JwtPrincipalTestFactory {

    /** 기본 테스트 사용자 ID. */
    public static final long DEFAULT_USER_ID = 100L;

    /** 기본 테스트 사용자명. */
    public static final String DEFAULT_USERNAME = "testuser";

    private JwtPrincipalTestFactory() {
        // 정적 헬퍼 — 인스턴스화 방지
    }

    /**
     * 기본 JwtPrincipal — userId=100, username=testuser, 권한 없음.
     */
    public static JwtPrincipal testPrincipal() {
        return new JwtPrincipal(DEFAULT_USER_ID, DEFAULT_USERNAME, Set.of(), Set.of());
    }

    /**
     * 역할 1개를 부여한 JwtPrincipal — 'ROLE_' prefix 자동 부여.
     *
     * @param role 역할 이름 ('ROLE_' prefix 제외, 예: "ADMIN")
     */
    public static JwtPrincipal withRole(String role) {
        return new JwtPrincipal(DEFAULT_USER_ID, DEFAULT_USERNAME, Set.of(role), Set.of());
    }

    /**
     * 역할 다수를 부여한 JwtPrincipal.
     */
    public static JwtPrincipal withRoles(String... roles) {
        return new JwtPrincipal(DEFAULT_USER_ID, DEFAULT_USERNAME, Set.of(roles), Set.of());
    }

    /**
     * 권한 (Authority) 1개를 부여한 JwtPrincipal — Authority 는 prefix 없이 그대로 사용.
     *
     * @param authority 권한 이름 (예: "DASHBOARD:LAYOUT:READ")
     */
    public static JwtPrincipal withAuthority(String authority) {
        return new JwtPrincipal(DEFAULT_USER_ID, DEFAULT_USERNAME, Set.of(), Set.of(authority));
    }

    /**
     * 권한 다수를 부여한 JwtPrincipal.
     */
    public static JwtPrincipal withAuthorities(String... authorities) {
        return new JwtPrincipal(DEFAULT_USER_ID, DEFAULT_USERNAME, Set.of(), Set.of(authorities));
    }

    /**
     * userId 와 권한 조합으로 JwtPrincipal 구성.
     */
    public static JwtPrincipal of(long userId, String username, Set<String> roles, Set<String> authorities) {
        return new JwtPrincipal(userId, username, roles, authorities);
    }

    /**
     * JwtPrincipal 을 Spring Security 인증 토큰으로 래핑.
     * JwtPrincipal.getAuthorities() 가 'ROLE_' prefix 자동 부여 → 그대로 권한 목록으로 사용.
     */
    public static UsernamePasswordAuthenticationToken token(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }

    /**
     * MockMvc {@code .with(...)} 호출용 RequestPostProcessor — 인증 토큰을 SecurityContext 에 주입.
     *
     * <p>예: {@code mockMvc.perform(post(...).with(jwtAuth(testPrincipal())))}.
     */
    public static RequestPostProcessor jwtAuth(JwtPrincipal principal) {
        return SecurityMockMvcRequestPostProcessors.authentication(token(principal));
    }
}
