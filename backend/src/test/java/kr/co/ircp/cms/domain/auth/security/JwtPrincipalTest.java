package kr.co.ircp.cms.domain.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JwtPrincipal} 단위 테스트 — SPEC-CMS-002 Step 3 REFACTOR.
 *
 * <p>커버리지 갭 P1: 71.4% → 100% (LINE)
 * <p>대상 ANCHOR: SecurityContext Principal 계약 (fan_in ≥ 3)
 */
@DisplayName("JwtPrincipal — SecurityContext Principal 계약")
class JwtPrincipalTest {

    @Test
    @DisplayName("호환 생성자 — permissions 빈 Set으로 초기화")
    void compatibilityConstructorShouldInitializeEmptyPermissions() {
        JwtPrincipal p = new JwtPrincipal(7L, "alice", Set.of("ADMIN"));

        assertThat(p.userId()).isEqualTo(7L);
        assertThat(p.username()).isEqualTo("alice");
        assertThat(p.roles()).containsExactly("ADMIN");
        assertThat(p.permissions()).isEmpty();
    }

    @Test
    @DisplayName("4-인자 생성자 — roles+permissions 모두 보존")
    void fullConstructorShouldPreserveAllFields() {
        JwtPrincipal p = new JwtPrincipal(
                42L,
                "bob",
                Set.of("USER", "EDITOR"),
                Set.of("BOARD_READ", "BOARD_WRITE"));

        assertThat(p.userId()).isEqualTo(42L);
        assertThat(p.username()).isEqualTo("bob");
        assertThat(p.roles()).containsExactlyInAnyOrder("USER", "EDITOR");
        assertThat(p.permissions()).containsExactlyInAnyOrder("BOARD_READ", "BOARD_WRITE");
    }

    @Test
    @DisplayName("getAuthorities — role은 ROLE_ 접두 후 GrantedAuthority로 변환")
    void getAuthoritiesShouldPrefixRolesWithRoleUnderscore() {
        JwtPrincipal p = new JwtPrincipal(1L, "u", Set.of("ADMIN", "USER"), Set.of());

        Collection<? extends GrantedAuthority> auths = p.getAuthorities();
        Set<String> values = auths.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(values).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("getAuthorities — permission은 ROLE_ 접두 없이 그대로 사용")
    void getAuthoritiesShouldKeepPermissionsAsIs() {
        JwtPrincipal p = new JwtPrincipal(
                1L, "u", Set.of("USER"), Set.of("BOARD_READ", "BOARD_WRITE"));

        Set<String> values = p.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(values).containsExactlyInAnyOrder(
                "ROLE_USER", "BOARD_READ", "BOARD_WRITE");
    }

    @Test
    @DisplayName("getAuthorities — roles/permissions 모두 비어 있으면 빈 컬렉션")
    void getAuthoritiesShouldReturnEmptyWhenNoRolesAndNoPermissions() {
        JwtPrincipal p = new JwtPrincipal(1L, "u", Set.of(), Set.of());

        assertThat(p.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("UserDetails 계약 — getUsername() 은 username 반환, getPassword() 는 null")
    void userDetailsContract() {
        JwtPrincipal p = new JwtPrincipal(1L, "charlie", Set.of("USER"));

        assertThat(p.getUsername()).isEqualTo("charlie");
        assertThat(p.getPassword()).isNull();
    }

    @Test
    @DisplayName("UserDetails 계약 — 모든 boolean 상태 플래그는 항상 true")
    void userDetailsBooleanFlagsAreAlwaysTrue() {
        JwtPrincipal p = new JwtPrincipal(1L, "u", Set.of("USER"));

        assertThat(p.isAccountNonExpired()).isTrue();
        assertThat(p.isAccountNonLocked()).isTrue();
        assertThat(p.isCredentialsNonExpired()).isTrue();
        assertThat(p.isEnabled()).isTrue();
    }
}
