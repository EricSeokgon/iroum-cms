package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.BulkStatusResult;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.OrganizationMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService.bulkUpdateStatus 단위 테스트.
 *
 * <p>SPEC-CMS-USER-BULK-STATUS-001 — 일괄 상태 변경 부분 실패 허용 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService.bulkUpdateStatus 단위 테스트")
class BulkUpdateStatusTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private OrganizationMapper organizationMapper;
    @Mock private PermissionChangeHistoryService permissionChangeHistoryService;
    @Mock private EmailEncryptionService emailEncryptionService;
    @Mock private PersonalDataAccessLogService personalDataAccessLogService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, refreshTokenMapper,
                passwordPolicyService, organizationMapper, permissionChangeHistoryService,
                emailEncryptionService, personalDataAccessLogService);
    }

    private User userWithStatus(long id, UserStatus status) {
        return User.builder().id(id).username("user" + id).status(status).build();
    }

    @Test
    @DisplayName("모든 사용자 ACTIVE 전환 — successCount=N, failureCount=0")
    void allUsersActiveSuccess() {
        when(userMapper.findById(1L)).thenReturn(Optional.of(userWithStatus(1L, UserStatus.INACTIVE)));
        when(userMapper.findById(2L)).thenReturn(Optional.of(userWithStatus(2L, UserStatus.INACTIVE)));
        when(userMapper.findById(3L)).thenReturn(Optional.of(userWithStatus(3L, UserStatus.INACTIVE)));

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(1L, 2L, 3L), "ACTIVE", "100", "DEPT_ADMIN");

        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isZero();
        assertThat(result.failures()).isEmpty();
        verify(userMapper, never()).softDelete(anyLong(), any());
    }

    @Test
    @DisplayName("존재하지 않는 userId — 실패 항목 추가")
    void unknownUserIdFails() {
        when(userMapper.findById(99L)).thenReturn(Optional.empty());

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(99L), "ACTIVE", "100", "DEPT_ADMIN");

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).userId()).isEqualTo(99L);
        assertThat(result.failures().get(0).reason()).isEqualTo("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("DELETED 상태 사용자 — 변경 불가 실패")
    void deletedUserFails() {
        when(userMapper.findById(5L)).thenReturn(Optional.of(userWithStatus(5L, UserStatus.DELETED)));

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(5L), "ACTIVE", "100", "SUPER_ADMIN");

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason()).isEqualTo("DELETED 상태는 변경할 수 없습니다");
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("targetStatus=DELETED + 비 SUPER_ADMIN — 권한 실패")
    void deleteByNonSuperAdminFails() {
        when(userMapper.findById(7L)).thenReturn(Optional.of(userWithStatus(7L, UserStatus.ACTIVE)));

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(7L), "DELETED", "100", "DEPT_ADMIN");

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures().get(0).reason())
                .isEqualTo("SUPER_ADMIN만 DELETED로 변경할 수 있습니다");
        verify(userMapper, never()).softDelete(anyLong(), any());
    }

    @Test
    @DisplayName("부분 실패 — 2 성공 1 실패")
    void partialFailure() {
        when(userMapper.findById(1L)).thenReturn(Optional.of(userWithStatus(1L, UserStatus.ACTIVE)));
        when(userMapper.findById(2L)).thenReturn(Optional.empty());
        when(userMapper.findById(3L)).thenReturn(Optional.of(userWithStatus(3L, UserStatus.INACTIVE)));

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(1L, 2L, 3L), "INACTIVE", "100", "DEPT_ADMIN");

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).userId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("LOCKED → ACTIVE 전환 — 잠금 해제(unlock) 호출")
    void lockedToActiveCallsUnlock() {
        when(userMapper.findById(8L)).thenReturn(Optional.of(userWithStatus(8L, UserStatus.LOCKED)));

        BulkStatusResult result = userService.bulkUpdateStatus(
                List.of(8L), "ACTIVE", "100", "SUPER_ADMIN");

        assertThat(result.successCount()).isEqualTo(1);
        verify(userMapper).unlock(eq(8L), any());
        verify(userMapper, never()).update(any());
    }
}
