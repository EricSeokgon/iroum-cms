package kr.co.ircp.cms.domain.approval.service;

import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException;
import kr.co.ircp.cms.domain.approval.repository.UserApprovalMapper;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.service.EmailService;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-USER-APPROVAL-001 — 가입 승인 서비스 단위 테스트.
 *
 * <p>단건 승인/거절(REQ-UA-010~013), 일괄 처리(REQ-UA-014~016), 이메일 통지(REQ-UA-017/018).
 * 트랜잭션 동기화가 비활성(단위 테스트)일 때 이메일은 즉시 실행되어 검증 가능하다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApprovalService 단위 테스트 (SPEC-CMS-USER-APPROVAL-001)")
class UserApprovalServiceImplTest {

    @Mock private UserApprovalMapper approvalMapper;
    @Mock private UserMapper userMapper;
    @Mock private EmailEncryptionService emailEncryptionService;
    @Mock private EmailService emailService;

    private UserApprovalServiceImpl service;

    private static final long OPERATOR = 1L;

    @BeforeEach
    void setUp() {
        // self-proxy 자리에 실제 인스턴스를 주입 — 단위 테스트에서는 트랜잭션 미적용이므로 직접 호출과 동일.
        service = new UserApprovalServiceImpl(
                approvalMapper, userMapper, emailEncryptionService, emailService, null);
    }

    private User pendingUser(long id, String name) {
        return User.builder()
                .id(id).username("u" + id + "@example.com").name(name)
                .status(UserStatus.PENDING_APPROVAL)
                .build();
    }

    // ─── 승인 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-UA-010 — 승인: ACTIVE 전환 + MEMBER 역할 부여 + 확인 이메일 발송")
    void approve_pending_setsActiveAndSendsEmail() {
        when(approvalMapper.updateApprovalStatus(eq(10L), eq("ACTIVE"), eq(null), eq(OPERATOR), any()))
                .thenReturn(1);
        when(userMapper.findRoleCodesByUserId(10L)).thenReturn(Set.of()); // MEMBER 없음
        User u = pendingUser(10L, "홍길동");
        u.setEmail("u10@example.com");
        when(userMapper.findById(10L)).thenReturn(Optional.of(u));

        service.approve(10L, OPERATOR);

        verify(approvalMapper).updateApprovalStatus(eq(10L), eq("ACTIVE"), eq(null), eq(OPERATOR), any());
        verify(userMapper).insertRole(eq(10L), eq("MEMBER"), eq(OPERATOR), any());
        verify(emailService).sendApprovalConfirmed(eq("u10@example.com"), eq("홍길동"));
    }

    @Test
    @DisplayName("REQ-UA-010 — 승인: MEMBER 역할이 이미 있으면 중복 부여하지 않음")
    void approve_existingMember_doesNotReInsertRole() {
        when(approvalMapper.updateApprovalStatus(eq(11L), eq("ACTIVE"), eq(null), eq(OPERATOR), any()))
                .thenReturn(1);
        when(userMapper.findRoleCodesByUserId(11L)).thenReturn(Set.of("MEMBER"));
        User u = pendingUser(11L, "김철수");
        u.setEmail("u11@example.com");
        when(userMapper.findById(11L)).thenReturn(Optional.of(u));

        service.approve(11L, OPERATOR);

        verify(userMapper, never()).insertRole(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("REQ-UA-013 — 승인: 대기 상태가 아니면 409(UserNotPendingApprovalException)")
    void approve_notPending_throwsConflict() {
        when(approvalMapper.updateApprovalStatus(eq(12L), eq("ACTIVE"), eq(null), eq(OPERATOR), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.approve(12L, OPERATOR))
                .isInstanceOf(UserNotPendingApprovalException.class);

        verify(userMapper, never()).insertRole(anyLong(), anyString(), any(), any());
        verify(emailService, never()).sendApprovalConfirmed(anyString(), anyString());
    }

    // ─── 거절 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-UA-011 — 거절: INACTIVE 전환 + 사유 저장 + 거절 이메일(사유 포함)")
    void reject_pending_setsInactiveAndSendsEmail() {
        when(approvalMapper.updateApprovalStatus(eq(20L), eq("INACTIVE"), eq("부적격"), eq(OPERATOR), any()))
                .thenReturn(1);
        User u = pendingUser(20L, "이영희");
        u.setEmail("u20@example.com");
        when(userMapper.findById(20L)).thenReturn(Optional.of(u));

        service.reject(20L, "부적격", OPERATOR);

        verify(approvalMapper).updateApprovalStatus(eq(20L), eq("INACTIVE"), eq("부적격"), eq(OPERATOR), any());
        verify(emailService).sendApprovalRejected(eq("u20@example.com"), eq("이영희"), eq("부적격"));
    }

    @Test
    @DisplayName("REQ-UA-012 — 거절: 사유가 비어 있으면 400(IllegalArgumentException)")
    void reject_blankReason_throwsBadRequest() {
        assertThatThrownBy(() -> service.reject(21L, "  ", OPERATOR))
                .isInstanceOf(IllegalArgumentException.class);

        verify(approvalMapper, never()).updateApprovalStatus(anyLong(), anyString(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("REQ-UA-013 — 거절: 대기 상태가 아니면 409")
    void reject_notPending_throwsConflict() {
        when(approvalMapper.updateApprovalStatus(eq(22L), eq("INACTIVE"), eq("사유"), eq(OPERATOR), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.reject(22L, "사유", OPERATOR))
                .isInstanceOf(UserNotPendingApprovalException.class);

        verify(emailService, never()).sendApprovalRejected(anyString(), anyString(), anyString());
    }

    // ─── 일괄 처리 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-UA-014/016 — 일괄 승인: 혼합 성공/실패 집계, 개별 실패가 전체를 막지 않음")
    void bulkApprove_mixedResults_aggregates() {
        // self-proxy 자리에 단건 메서드를 스텁한 mock 을 주입하여 일괄 집계 로직만 검증한다.
        UserApprovalService selfMock = org.mockito.Mockito.mock(UserApprovalService.class);
        UserApprovalServiceImpl bulkService = new UserApprovalServiceImpl(
                approvalMapper, userMapper, emailEncryptionService, emailService, selfMock);
        // 30 성공, 31 실패(409)
        org.mockito.Mockito.doNothing().when(selfMock).approve(eq(30L), eq(OPERATOR));
        org.mockito.Mockito.doThrow(new UserNotPendingApprovalException(31L))
                .when(selfMock).approve(eq(31L), eq(OPERATOR));

        BulkOperationResult result = bulkService.bulkApprove(List.of(30L, 31L), OPERATOR);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).userId()).isEqualTo(31L);
    }

    @Test
    @DisplayName("REQ-UA-015/016 — 일괄 거절: 공통 사유로 건별 처리 집계")
    void bulkReject_appliesCommonReason() {
        UserApprovalService selfMock = org.mockito.Mockito.mock(UserApprovalService.class);
        UserApprovalServiceImpl bulkService = new UserApprovalServiceImpl(
                approvalMapper, userMapper, emailEncryptionService, emailService, selfMock);
        org.mockito.Mockito.doNothing().when(selfMock).reject(eq(40L), eq("일괄 사유"), eq(OPERATOR));
        org.mockito.Mockito.doNothing().when(selfMock).reject(eq(41L), eq("일괄 사유"), eq(OPERATOR));

        BulkOperationResult result = bulkService.bulkReject(List.of(40L, 41L), "일괄 사유", OPERATOR);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
    }

    @Test
    @DisplayName("REQ-UA-015 — 일괄 거절: 공통 사유가 비어 있으면 400")
    void bulkReject_blankReason_throwsBadRequest() {
        assertThatThrownBy(() -> service.bulkReject(List.of(50L), "", OPERATOR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
