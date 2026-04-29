package kr.co.ircp.cms.domain.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JWT 인증 완료 후 SecurityContext에 저장되는 Principal.
 *
 * <p>SPEC-CMS-002 Step 3 REFACTOR — Authentication.getPrincipal()로 컨트롤러에서 추출.
 * UserDetails 구현으로 SecurityMockMvcRequestPostProcessors.user() 테스트 호환성 유지.
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
public record JwtPrincipal(long userId, String username, Set<String> roles, Set<String> permissions)
        implements UserDetails {

    /**
     * 기존 호환 생성자 (permissions 빈 Set).
     *
     * <p>REQ-AUTH-013 이전 코드와의 호환성 유지.
     */
    public JwtPrincipal(long userId, String username, Set<String> roles) {
        this(userId, username, roles, Set.of());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(roles.stream(), permissions.stream())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
