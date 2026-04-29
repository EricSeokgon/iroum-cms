package kr.co.ircp.cms.domain.auth.security;

import java.util.Set;

/**
 * JWT 인증 완료 후 SecurityContext에 저장되는 Principal.
 *
 * <p>SPEC-CMS-002 Step 3 REFACTOR — Authentication.getPrincipal()로 컨트롤러에서 추출.
 *
 * <pre>
 * &#64;GetMapping("/me")
 * public Map&#60;String, Object&#62; me(&#64;AuthenticationPrincipal JwtPrincipal principal) {
 *     return Map.of("userId", principal.userId(), "username", principal.username());
 * }
 * </pre>
 */
// @MX:ANCHOR: [AUTO] JwtPrincipal — SecurityContext Principal 계약; 변경 시 모든 컨트롤러 영향
// @MX:REASON: JwtAuthenticationFilter, AuditLogAspect, 컨트롤러 @AuthenticationPrincipal 참조 (fan_in >= 3)
public record JwtPrincipal(long userId, String username, Set<String> roles, Set<String> permissions) {

    /**
     * 기존 호환 생성자 (permissions 빈 Set).
     *
     * <p>REQ-AUTH-013 이전 코드와의 호환성 유지.
     */
    public JwtPrincipal(long userId, String username, Set<String> roles) {
        this(userId, username, roles, Set.of());
    }
}
