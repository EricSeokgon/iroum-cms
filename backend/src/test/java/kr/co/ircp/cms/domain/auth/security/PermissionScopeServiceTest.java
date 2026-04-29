package kr.co.ircp.cms.domain.auth.security;

import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.OrganizationStatus;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.AccessOutOfScopeException;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * PermissionScopeService 단위 테스트.
 *
 * <p>SPEC-CMS-002 Q-24 — DEPT_ADMIN 범위 제한 서비스 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionScopeService 단위 테스트")
class PermissionScopeServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private OrganizationMapper organizationMapper;

    private PermissionScopeService scopeService;

    @BeforeEach
    void setUp() {
        scopeService = new PermissionScopeService(userMapper, organizationMapper);
    }

    @Test
    @DisplayName("canAccessUser — SUPER_ADMIN은 모든 사용자 접근 허용")
    void canAccessUser_superAdmin_alwaysTrue() {
        JwtPrincipal superAdmin = new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());

        boolean result = scopeService.canAccessUser(superAdmin, 999L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser — DEPT_ADMIN이 자신의 부서 사용자에 접근 가능")
    void canAccessUser_deptAdmin_sameOrg_returnsTrue() {
        JwtPrincipal deptAdmin = new JwtPrincipal(2L, "manager", Set.of("DEPT_ADMIN"), Set.of());

        // actor: org_id=3, path=/1/3/
        User actorUser = userWithOrg(2L, 3L);
        Organization actorOrg = org(3L, "/1/3/");
        // target: org_id=3, path=/1/3/ (같은 조직)
        User targetUser = userWithOrg(10L, 3L);
        Organization targetOrg = org(3L, "/1/3/");

        when(userMapper.findById(2L)).thenReturn(Optional.of(actorUser));
        when(organizationMapper.findById(3L)).thenReturn(Optional.of(actorOrg))
                .thenReturn(Optional.of(targetOrg)); // 두 번째 findById 호출 (target)
        when(userMapper.findById(10L)).thenReturn(Optional.of(targetUser));

        boolean result = scopeService.canAccessUser(deptAdmin, 10L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser — DEPT_ADMIN이 자손 조직 사용자에 접근 가능")
    void canAccessUser_deptAdmin_childOrg_returnsTrue() {
        JwtPrincipal deptAdmin = new JwtPrincipal(2L, "manager", Set.of("DEPT_ADMIN"), Set.of());

        // actor: org_id=3, path=/1/3/
        User actorUser = userWithOrg(2L, 3L);
        Organization actorOrg = org(3L, "/1/3/");
        // target: org_id=7, path=/1/3/7/ (자손 조직)
        User targetUser = userWithOrg(10L, 7L);
        Organization targetOrg = org(7L, "/1/3/7/");

        when(userMapper.findById(2L)).thenReturn(Optional.of(actorUser));
        when(organizationMapper.findById(3L)).thenReturn(Optional.of(actorOrg));
        when(userMapper.findById(10L)).thenReturn(Optional.of(targetUser));
        when(organizationMapper.findById(7L)).thenReturn(Optional.of(targetOrg));

        boolean result = scopeService.canAccessUser(deptAdmin, 10L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccessUser — DEPT_ADMIN이 다른 부서 사용자에 접근 불가")
    void canAccessUser_deptAdmin_differentOrg_returnsFalse() {
        JwtPrincipal deptAdmin = new JwtPrincipal(2L, "manager", Set.of("DEPT_ADMIN"), Set.of());

        // actor: org_id=3, path=/1/3/
        User actorUser = userWithOrg(2L, 3L);
        Organization actorOrg = org(3L, "/1/3/");
        // target: org_id=5, path=/1/5/ (다른 부서)
        User targetUser = userWithOrg(10L, 5L);
        Organization targetOrg = org(5L, "/1/5/");

        when(userMapper.findById(2L)).thenReturn(Optional.of(actorUser));
        when(organizationMapper.findById(3L)).thenReturn(Optional.of(actorOrg));
        when(userMapper.findById(10L)).thenReturn(Optional.of(targetUser));
        when(organizationMapper.findById(5L)).thenReturn(Optional.of(targetOrg));

        boolean result = scopeService.canAccessUser(deptAdmin, 10L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("requireUserAccess — 접근 불가 시 AccessOutOfScopeException 발생")
    void requireUserAccess_throwsException_whenOutOfScope() {
        JwtPrincipal deptAdmin = new JwtPrincipal(2L, "manager", Set.of("DEPT_ADMIN"), Set.of());

        // actor: org 미배정
        User actorUser = userWithOrg(2L, null);

        when(userMapper.findById(2L)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> scopeService.requireUserAccess(deptAdmin, 10L))
                .isInstanceOf(AccessOutOfScopeException.class);
    }

    @Test
    @DisplayName("canAccessOrganization — SUPER_ADMIN은 모든 조직 접근 허용")
    void canAccessOrganization_superAdmin_alwaysTrue() {
        JwtPrincipal superAdmin = new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());

        boolean result = scopeService.canAccessOrganization(superAdmin, 999L);

        assertThat(result).isTrue();
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────

    private User userWithOrg(long userId, Long orgId) {
        return User.builder()
                .id(userId)
                .username("user" + userId)
                .status(UserStatus.ACTIVE)
                .organizationId(orgId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Organization org(long id, String path) {
        return Organization.builder()
                .id(id)
                .path(path)
                .status(OrganizationStatus.ACTIVE)
                .build();
    }
}
