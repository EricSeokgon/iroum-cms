package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.RoleCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.RoleDetail;
import kr.co.ircp.cms.domain.auth.dto.RoleSummary;
import kr.co.ircp.cms.domain.auth.dto.RoleUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Role;
import kr.co.ircp.cms.domain.auth.exception.RoleHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.SystemRoleProtectedException;
import kr.co.ircp.cms.domain.auth.repository.RoleMapper;
import kr.co.ircp.cms.domain.auth.repository.RolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoleService 단위 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — Mockito 기반 역할 CRUD 서비스 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService 단위 테스트")
class RoleServiceTest {

    @Mock private RoleMapper roleMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private PermissionService permissionService;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleMapper, rolePermissionMapper, permissionService);
    }

    @Test
    @DisplayName("findAll — 역할 목록 반환")
    void findAll_returnsRoleSummaryList() {
        List<RoleSummary> rows = List.of(
                new RoleSummary("SUPER_ADMIN", "최고관리자", null, true, null, 1, 15, Instant.now())
        );
        when(roleMapper.findAll()).thenReturn(rows);

        List<RoleSummary> result = roleService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("findByCode — 존재하는 역할 반환 (권한 포함)")
    void findByCode_returnsDetail_whenExists() {
        Role role = systemRole("SUPER_ADMIN", "최고관리자");
        when(roleMapper.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(role));
        when(roleMapper.countUsers("SUPER_ADMIN")).thenReturn(2);
        when(permissionService.findEffectivePermissionsForRole("SUPER_ADMIN"))
                .thenReturn(Set.of("USER:READ", "USER:WRITE"));

        RoleDetail detail = roleService.findByCode("SUPER_ADMIN");

        assertThat(detail.code()).isEqualTo("SUPER_ADMIN");
        assertThat(detail.permissionCodes()).containsExactlyInAnyOrder("USER:READ", "USER:WRITE");
        assertThat(detail.userCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByCode — 존재하지 않으면 404 ResponseStatusException")
    void findByCode_throws404_whenAbsent() {
        when(roleMapper.findByCode("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.findByCode("GHOST"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("create — 정상 생성 시 insert + insertBatch 호출")
    void create_insertsRoleAndPermissions() {
        RoleCreateRequest req = new RoleCreateRequest(
                "NEW_ROLE", "새역할", "설명", Set.of("USER:READ"));
        when(roleMapper.existsByCode("NEW_ROLE")).thenReturn(false);

        Role createdRole = customRole("NEW_ROLE", "새역할");
        when(roleMapper.findByCode("NEW_ROLE")).thenReturn(Optional.of(createdRole));
        when(roleMapper.countUsers("NEW_ROLE")).thenReturn(0);
        when(permissionService.findEffectivePermissionsForRole("NEW_ROLE"))
                .thenReturn(Set.of("USER:READ"));

        RoleDetail result = roleService.create(req, 1L);

        verify(roleMapper).insert(any(Role.class));
        verify(rolePermissionMapper).insertBatch(eq("NEW_ROLE"), eq(Set.of("USER:READ")),
                eq(1L), any(Instant.class));
        assertThat(result.code()).isEqualTo("NEW_ROLE");
    }

    @Test
    @DisplayName("create — 코드 중복 시 409 ResponseStatusException")
    void create_throws409_whenDuplicateCode() {
        RoleCreateRequest req = new RoleCreateRequest(
                "SUPER_ADMIN", "이미있음", null, Set.of());
        when(roleMapper.existsByCode("SUPER_ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> roleService.create(req, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(roleMapper, never()).insert(any());
    }

    @Test
    @DisplayName("delete — is_system=true 역할 삭제 시 SystemRoleProtectedException (HTTP 400)")
    void delete_throws400_whenSystemRole() {
        Role sysRole = systemRole("SUPER_ADMIN", "최고관리자");
        when(roleMapper.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(sysRole));

        assertThatThrownBy(() -> roleService.delete("SUPER_ADMIN", 1L))
                .isInstanceOf(SystemRoleProtectedException.class);
        verify(roleMapper, never()).delete(anyString());
    }

    @Test
    @DisplayName("delete — 사용자 매핑 존재 시 RoleHasUsersException (HTTP 409)")
    void delete_throws409_whenRoleHasUsers() {
        Role role = customRole("CUSTOM_ROLE", "커스텀");
        when(roleMapper.findByCode("CUSTOM_ROLE")).thenReturn(Optional.of(role));
        when(roleMapper.countUsers("CUSTOM_ROLE")).thenReturn(3);

        assertThatThrownBy(() -> roleService.delete("CUSTOM_ROLE", 1L))
                .isInstanceOf(RoleHasUsersException.class);
        verify(roleMapper, never()).delete(anyString());
    }

    @Test
    @DisplayName("delete — 정상 삭제 시 deleteByRole + delete 호출")
    void delete_removesPermissionsAndRole() {
        Role role = customRole("UNUSED_ROLE", "미사용역할");
        when(roleMapper.findByCode("UNUSED_ROLE")).thenReturn(Optional.of(role));
        when(roleMapper.countUsers("UNUSED_ROLE")).thenReturn(0);

        roleService.delete("UNUSED_ROLE", 1L);

        verify(rolePermissionMapper).deleteByRole("UNUSED_ROLE");
        verify(roleMapper).delete("UNUSED_ROLE");
    }

    @Test
    @DisplayName("updatePermissions — deleteByRole + insertBatch 호출")
    void updatePermissions_replacesPermissions() {
        Role role = customRole("EDITOR", "편집자");
        when(roleMapper.findByCode("EDITOR")).thenReturn(Optional.of(role));

        roleService.updatePermissions("EDITOR", Set.of("USER:READ", "AUDIT:READ"), 1L);

        verify(rolePermissionMapper).deleteByRole("EDITOR");
        verify(rolePermissionMapper).insertBatch(eq("EDITOR"),
                eq(Set.of("USER:READ", "AUDIT:READ")), eq(1L), any(Instant.class));
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────

    private Role systemRole(String code, String name) {
        return Role.builder()
                .code(code).name(name).isSystem(true)
                .createdAt(Instant.now())
                .build();
    }

    private Role customRole(String code, String name) {
        return Role.builder()
                .code(code).name(name).isSystem(false)
                .createdAt(Instant.now())
                .build();
    }
}
