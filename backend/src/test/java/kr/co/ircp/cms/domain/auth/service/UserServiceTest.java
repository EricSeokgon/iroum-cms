package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;
import kr.co.ircp.cms.domain.auth.entity.Organization;
import kr.co.ircp.cms.domain.auth.entity.OrganizationStatus;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.exception.DuplicateUserException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.UserNotFoundException;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 단위 테스트 (GREEN 단계 — 새 도메인, 처음부터 GREEN).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — Mockito 기반 서비스 레이어 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService GREEN 단계 테스트")
class UserServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private OrganizationMapper organizationMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, refreshTokenMapper,
                passwordPolicyService, organizationMapper);
    }

    // ──────────────────────────────────────────────────────────────
    // findPage
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPage — 정상 페이징 결과 반환")
    void findPage_returnsPagedResults() {
        List<UserSummary> rows = List.of(
                new UserSummary(1L, "uuid-1", "admin", "admin@test.com",
                        "관리자", "ACTIVE", null, Instant.now())
        );
        when(userMapper.findPage(anyInt(), anyInt(), isNull(), isNull(), anyString()))
                .thenReturn(rows);
        when(userMapper.countAll(isNull(), isNull())).thenReturn(1L);

        PageResponse<UserSummary> result = userService.findPage(0, 20, "createdAt,desc", null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("findPage — search 파라미터 전달 검증")
    void findPage_appliesSearchFilter() {
        when(userMapper.findPage(eq(0), eq(20), eq("kim"), isNull(), anyString()))
                .thenReturn(List.of());
        when(userMapper.countAll(eq("kim"), isNull())).thenReturn(0L);

        PageResponse<UserSummary> result = userService.findPage(0, 20, "createdAt,desc", "kim", null);

        assertThat(result.content()).isEmpty();
        verify(userMapper).findPage(0, 20, "kim", null, "createdAt,desc");
    }

    // ──────────────────────────────────────────────────────────────
    // findById
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById — 존재하는 사용자 반환")
    void findById_returnsDetail_whenExists() {
        User user = activeUser(1L, "admin");
        when(userMapper.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.findRoleCodesByUserId(1L)).thenReturn(Set.of("SUPER_ADMIN"));

        UserDetail detail = userService.findById(1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.username()).isEqualTo("admin");
        assertThat(detail.roleCodes()).containsExactly("SUPER_ADMIN");
    }

    @Test
    @DisplayName("findById — 존재하지 않으면 UserNotFoundException")
    void findById_throwsUserNotFound_whenAbsent() {
        when(userMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────
    // create
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 정상 생성 시 insert + insertRole 호출")
    void create_passesValidation_thenInserts() {
        UserCreateRequest req = new UserCreateRequest(
                "newuser", "new@test.com", "ValidP@ss123!",
                "새사용자", "ACTIVE", Set.of("VIEWER"));

        when(userMapper.existsByUsername("newuser")).thenReturn(false);
        when(userMapper.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordPolicyService.hash("ValidP@ss123!")).thenReturn("$2a$12$hashed");

        // insert 호출 후 id가 채워지도록 doAnswer 사용
        org.mockito.Mockito.doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u = User.builder()
                    .id(10L).username(u.getUsername()).email(u.getEmail())
                    .passwordHash(u.getPasswordHash()).name(u.getName())
                    .status(u.getStatus()).createdAt(Instant.now()).updatedAt(Instant.now())
                    .build();
            // setId via reflection (Lombok @Data)
            try {
                java.lang.reflect.Field f = User.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(invocation.getArgument(0), 10L);
            } catch (Exception e) { /* ignore in test */ }
            return null;
        }).when(userMapper).insert(any(User.class));

        when(userMapper.findById(anyLong())).thenReturn(Optional.of(activeUser(10L, "newuser")));
        when(userMapper.findRoleCodesByUserId(anyLong())).thenReturn(Set.of("VIEWER"));

        UserDetail result = userService.create(req, 1L);

        verify(passwordPolicyService).validate("ValidP@ss123!");
        verify(userMapper).existsByUsername("newuser");
        verify(userMapper).existsByEmail("new@test.com");
        verify(userMapper).insert(any(User.class));
        verify(userMapper).insertRole(anyLong(), eq("VIEWER"), eq(1L), any(Instant.class));
        assertThat(result.username()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("create — username 중복 시 DuplicateUserException")
    void create_throwsDuplicateUser_whenUsernameExists() {
        UserCreateRequest req = new UserCreateRequest(
                "admin", "other@test.com", "ValidP@ss123!", "다른", null, Set.of());
        when(userMapper.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(req, 1L))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("username");
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create — email 중복 시 DuplicateUserException")
    void create_throwsDuplicateUser_whenEmailExists() {
        UserCreateRequest req = new UserCreateRequest(
                "unique", "admin@test.com", "ValidP@ss123!", "다른", null, Set.of());
        when(userMapper.existsByUsername("unique")).thenReturn(false);
        when(userMapper.existsByEmail("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(req, 1L))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("create — 비밀번호 정책 위반 시 PasswordPolicyViolationException")
    void create_throwsPasswordPolicy_whenWeak() {
        UserCreateRequest req = new UserCreateRequest(
                "newuser", "new@test.com", "weak", "이름", null, Set.of());
        doThrow(new PasswordPolicyViolationException("정책 위반"))
                .when(passwordPolicyService).validate("weak");

        assertThatThrownBy(() -> userService.create(req, 1L))
                .isInstanceOf(PasswordPolicyViolationException.class);
        verify(userMapper, never()).insert(any());
    }

    // ──────────────────────────────────────────────────────────────
    // update
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update — 역할 재설정 시 deleteRolesByUserId + insertRole 호출")
    void update_replacesRoles() {
        when(userMapper.findById(1L))
                .thenReturn(Optional.of(activeUser(1L, "admin")));
        when(userMapper.findRoleCodesByUserId(1L)).thenReturn(Set.of("EDITOR"));

        UserUpdateRequest req = new UserUpdateRequest(null, null, null, Set.of("EDITOR", "VIEWER"));
        userService.update(1L, req, 99L);

        verify(userMapper).deleteRolesByUserId(1L);
        verify(userMapper).insertRole(eq(1L), eq("EDITOR"), eq(99L), any(Instant.class));
        verify(userMapper).insertRole(eq(1L), eq("VIEWER"), eq(99L), any(Instant.class));
    }

    @Test
    @DisplayName("update — 존재하지 않으면 UserNotFoundException")
    void update_throwsUserNotFound() {
        when(userMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(999L,
                new UserUpdateRequest(null, null, null, null), 1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ──────────────────────────────────────────────────────────────
    // delete
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete — softDelete 호출 검증")
    void delete_marksDeletedAt() {
        when(userMapper.findById(5L)).thenReturn(Optional.of(activeUser(5L, "target")));

        userService.delete(5L, 1L);

        verify(userMapper).softDelete(eq(5L), any(Instant.class));
    }

    // ──────────────────────────────────────────────────────────────
    // unlock
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unlock — userMapper.unlock 호출 검증")
    void unlock_resetsLockedUntilAndFailCount() {
        when(userMapper.findById(3L)).thenReturn(Optional.of(activeUser(3L, "locked")));

        userService.unlock(3L, 1L);

        verify(userMapper).unlock(eq(3L), any(Instant.class));
    }

    // ──────────────────────────────────────────────────────────────
    // forceLogout
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("forceLogout — refreshTokenMapper.revokeAllForUser 호출 검증")
    void forceLogout_revokesAllRefreshTokens() {
        when(userMapper.findById(7L)).thenReturn(Optional.of(activeUser(7L, "target")));

        userService.forceLogout(7L, 1L);

        verify(refreshTokenMapper).revokeAllForUser(eq(7L), any(Instant.class));
    }

    // ──────────────────────────────────────────────────────────────
    // getMe / updateMe
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMe — 본인 정보 + 역할 반환")
    void getMe_returnsSelfWithRoles() {
        when(userMapper.findById(2L)).thenReturn(Optional.of(activeUser(2L, "me")));
        when(userMapper.findRoleCodesByUserId(2L)).thenReturn(Set.of("EDITOR"));

        UserSelf self = userService.getMe(2L);

        assertThat(self.username()).isEqualTo("me");
        assertThat(self.roleCodes()).containsExactly("EDITOR");
    }

    @Test
    @DisplayName("updateMe — 이메일·이름만 업데이트 (status·role 불변)")
    void updateMe_updatesEmailAndName_only() {
        User existing = activeUser(2L, "me");
        when(userMapper.findById(2L))
                .thenReturn(Optional.of(existing))  // updateMe 내 update 호출
                .thenReturn(Optional.of(existing)); // getMe 내 재조회
        when(userMapper.findRoleCodesByUserId(2L)).thenReturn(Set.of("EDITOR"));

        UserSelfUpdateRequest req = new UserSelfUpdateRequest("new@test.com", "새이름");
        UserSelf result = userService.updateMe(2L, req);

        // update 호출 (status·role 파라미터는 null)
        verify(userMapper).update(any(User.class));
        // insertRole·deleteRoles 비호출 검증 (역할 변경 없음)
        verify(userMapper, never()).deleteRolesByUserId(anyLong());
    }

    // ──────────────────────────────────────────────────────────────
    // findPage(actor) — Q-24 DEPT_ADMIN 범위 제한
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPage(actor) — SUPER_ADMIN은 orgPathPrefix 없이 전체 조회")
    void findPage_actor_superAdmin_noOrgPathPrefix() {
        JwtPrincipal superAdmin = new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());
        List<UserSummary> rows = List.of(
                new UserSummary(1L, "uuid-1", "admin", "admin@test.com",
                        "관리자", "ACTIVE", null, Instant.now())
        );
        when(userMapper.findPageWithScope(anyInt(), anyInt(), isNull(), isNull(), anyString(), isNull()))
                .thenReturn(rows);
        when(userMapper.countAllWithScope(isNull(), isNull(), isNull())).thenReturn(1L);

        PageResponse<UserSummary> result = userService.findPage(0, 20, "createdAt,desc", null, null, superAdmin);

        assertThat(result.content()).hasSize(1);
        // orgPathPrefix=null (전체 조회) 검증
        verify(userMapper).findPageWithScope(0, 20, null, null, "createdAt,desc", null);
        verify(userMapper).countAllWithScope(null, null, null);
    }

    @Test
    @DisplayName("findPage(actor) — DEPT_ADMIN은 소속 조직 path 접두사로 범위 제한")
    void findPage_actor_deptAdmin_appliesOrgPathPrefix() {
        // DEPT_ADMIN actor, org=/1/3/
        User actorUser = User.builder().id(2L).username("deptmgr")
                .organizationId(3L).status(UserStatus.ACTIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Organization actorOrg = Organization.builder().id(3L).path("/1/3/")
                .status(OrganizationStatus.ACTIVE).build();

        JwtPrincipal deptAdmin = new JwtPrincipal(2L, "deptmgr", Set.of("DEPT_ADMIN"), Set.of());

        when(userMapper.findById(2L)).thenReturn(Optional.of(actorUser));
        when(organizationMapper.findById(3L)).thenReturn(Optional.of(actorOrg));
        when(userMapper.findPageWithScope(anyInt(), anyInt(), isNull(), isNull(), anyString(), eq("/1/3/")))
                .thenReturn(List.of());
        when(userMapper.countAllWithScope(isNull(), isNull(), eq("/1/3/"))).thenReturn(0L);

        PageResponse<UserSummary> result = userService.findPage(0, 20, "createdAt,desc", null, null, deptAdmin);

        assertThat(result.totalElements()).isEqualTo(0);
        verify(userMapper).findPageWithScope(0, 20, null, null, "createdAt,desc", "/1/3/");
        verify(userMapper).countAllWithScope(null, null, "/1/3/");
    }

    @Test
    @DisplayName("findPage(actor) — DEPT_ADMIN이 조직 미배정 시 orgPathPrefix=null (전체 조회 차단 방지 — 조직 배정 필수)")
    void findPage_actor_deptAdmin_noOrg_orgPathPrefixNull() {
        // organization_id가 null인 DEPT_ADMIN
        User actorUser = User.builder().id(3L).username("deptmgr_noorg")
                .organizationId(null).status(UserStatus.ACTIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        JwtPrincipal deptAdmin = new JwtPrincipal(3L, "deptmgr_noorg", Set.of("DEPT_ADMIN"), Set.of());

        when(userMapper.findById(3L)).thenReturn(Optional.of(actorUser));
        when(userMapper.findPageWithScope(anyInt(), anyInt(), isNull(), isNull(), anyString(), isNull()))
                .thenReturn(List.of());
        when(userMapper.countAllWithScope(isNull(), isNull(), isNull())).thenReturn(0L);

        PageResponse<UserSummary> result = userService.findPage(0, 20, "createdAt,desc", null, null, deptAdmin);

        // orgPathPrefix=null — organizationMapper.findById 미호출 (actorOrgId가 null)
        verify(organizationMapper, never()).findById(anyLong());
        verify(userMapper).findPageWithScope(0, 20, null, null, "createdAt,desc", null);
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private User activeUser(long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@test.com")
                .passwordHash("$2a$12$hash")
                .name("테스트" + username)
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
