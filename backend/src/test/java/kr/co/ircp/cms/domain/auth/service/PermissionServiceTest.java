package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PermissionSummary;
import kr.co.ircp.cms.domain.auth.entity.Permission;
import kr.co.ircp.cms.domain.auth.repository.PermissionMapper;
import kr.co.ircp.cms.domain.auth.repository.RolePermissionMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionService 단위 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — 권한 카탈로그 및 실질 권한 집합 계산 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService 단위 테스트")
class PermissionServiceTest {

    @Mock private PermissionMapper permissionMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private UserMapper userMapper;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(permissionMapper, rolePermissionMapper, userMapper);
    }

    @Test
    @DisplayName("findAll — 전체 권한 카탈로그 반환")
    void findAll_returnsAllPermissions() {
        List<Permission> perms = List.of(
                Permission.builder().code("USER:READ").resource("USER").action("READ").description("사용자 조회").build(),
                Permission.builder().code("USER:WRITE").resource("USER").action("WRITE").description("사용자 생성/수정").build()
        );
        when(permissionMapper.findAll()).thenReturn(perms);

        List<PermissionSummary> result = permissionService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PermissionSummary::code)
                .containsExactlyInAnyOrder("USER:READ", "USER:WRITE");
    }

    @Test
    @DisplayName("findEffectivePermissionsForRole — 역할의 실질 권한 코드 반환")
    void findEffectivePermissionsForRole_delegatesToMapper() {
        when(rolePermissionMapper.findEffectivePermissionCodes("SUPER_ADMIN"))
                .thenReturn(Set.of("USER:READ", "USER:WRITE", "USER:DELETE"));

        Set<String> result = permissionService.findEffectivePermissionsForRole("SUPER_ADMIN");

        assertThat(result).containsExactlyInAnyOrder("USER:READ", "USER:WRITE", "USER:DELETE");
        verify(rolePermissionMapper).findEffectivePermissionCodes("SUPER_ADMIN");
    }

    @Test
    @DisplayName("findEffectivePermissionsForUser — 여러 역할의 권한 합산")
    void findEffectivePermissionsForUser_unionsRolePermissions() {
        when(userMapper.findRoleCodesByUserId(5L)).thenReturn(Set.of("EDITOR", "VIEWER"));
        when(rolePermissionMapper.findEffectivePermissionCodes("EDITOR"))
                .thenReturn(Set.of("USER:READ", "ORGANIZATION:READ"));
        when(rolePermissionMapper.findEffectivePermissionCodes("VIEWER"))
                .thenReturn(Set.of("USER:READ", "AUDIT:READ"));

        Set<String> result = permissionService.findEffectivePermissionsForUser(5L);

        // EDITOR + VIEWER 합산 (USER:READ 중복 제거)
        assertThat(result).containsExactlyInAnyOrder(
                "USER:READ", "ORGANIZATION:READ", "AUDIT:READ");
    }

    @Test
    @DisplayName("findEffectivePermissionsForUser — 역할 없는 사용자는 빈 집합 반환")
    void findEffectivePermissionsForUser_noRoles_returnsEmpty() {
        when(userMapper.findRoleCodesByUserId(99L)).thenReturn(Set.of());

        Set<String> result = permissionService.findEffectivePermissionsForUser(99L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findEffectivePermissionsForRole — SYSADMIN alias 시 findEffectivePermissionCodes에 위임")
    void findEffectivePermissionsForRole_sysadminAlias() {
        // SYSADMIN은 alias → alias 처리는 RolePermissionMapper.findEffectivePermissionCodes에서 처리
        when(rolePermissionMapper.findEffectivePermissionCodes("SYSADMIN"))
                .thenReturn(Set.of("USER:READ", "SYSTEM:ADMIN"));  // alias → SUPER_ADMIN 권한

        Set<String> result = permissionService.findEffectivePermissionsForRole("SYSADMIN");

        assertThat(result).containsExactlyInAnyOrder("USER:READ", "SYSTEM:ADMIN");
    }
}
