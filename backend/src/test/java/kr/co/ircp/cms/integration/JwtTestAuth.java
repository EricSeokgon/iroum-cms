package kr.co.ircp.cms.integration;

import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 통합 테스트(IT)용 JWT 인증 헬퍼.
 *
 * <p>{@code @WithMockUser} 어노테이션은 String username만 주입하므로
 * {@code @AuthenticationPrincipal JwtPrincipal} 추출 시 null이 발생한다.
 * 본 헬퍼는 {@link JwtPrincipal} record 객체를 직접 생성하여
 * MockMvc post-processor로 SecurityContext에 주입한다.
 *
 * <p>사용 예:
 * <pre>{@code
 * import static kr.co.ircp.cms.integration.JwtTestAuth.jwtAuth;
 *
 * mockMvc.perform(get("/api/v1/users/42")
 *         .with(jwtAuth(1L, "admin", "SUPER_ADMIN")))
 *     .andExpect(status().isOk());
 * }</pre>
 *
 * <p>SPEC-CMS-SECURITY-PII-002 RUN follow-up — IT 18건 NPE 회복용 헬퍼.
 */
// @MX:NOTE: [AUTO] @WithMockUser는 JwtPrincipal record를 주입하지 않음 (Spring Security 한계)
public final class JwtTestAuth {

    private JwtTestAuth() {
        // utility class
    }

    /**
     * 지정된 userId/username/roles로 인증된 MockMvc 요청 post-processor를 반환한다.
     *
     * @param userId   JwtPrincipal.userId 값
     * @param username 사용자 ID(이메일이 아닌 username)
     * @param roles    역할 목록 (예: "SUPER_ADMIN", "USER", "DEPT_ADMIN")
     */
    public static RequestPostProcessor jwtAuth(long userId, String username, String... roles) {
        JwtPrincipal principal = new JwtPrincipal(userId, username, Set.of(roles));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(auth);
    }
}
