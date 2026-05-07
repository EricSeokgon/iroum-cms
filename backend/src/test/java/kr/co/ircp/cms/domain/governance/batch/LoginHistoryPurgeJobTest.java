package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.repository.RetentionExecutionMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoginHistoryPurgeJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-009 — login_history 12개월 정리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginHistoryPurgeJob — 로그인 이력 정리 (REQ-GOV-009)")
class LoginHistoryPurgeJobTest {

    @Mock private RetentionPolicyService policyService;
    @Mock private RetentionExecutionMapper executionMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private LoginHistoryPurgeJob job;

    private RetentionPolicy policy() {
        return RetentionPolicy.builder()
                .id(4L)
                .targetTable("login_history")
                .policyType("DELETE")
                .retentionMonths(12)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — 정책 기반 deleteLoginHistory 호출 + 삭제 행 수 반환")
    void run_normal_deletesAndReturnsCount() {
        when(policyService.findByTargetTable("login_history")).thenReturn(Optional.of(policy()));
        when(executionMapper.deleteLoginHistory(12)).thenReturn(33);

        int processed = job.run();

        assertThat(processed).isEqualTo(33);
        verify(executionMapper).deleteLoginHistory(12);
    }

    @Test
    @DisplayName("run — 정책 미존재 시 IllegalStateException")
    void run_policyMissing_throws() {
        when(policyService.findByTargetTable("login_history")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention_policy not found");
    }

    @Test
    @DisplayName("scheduled — 정상 실행 시 batchLog success 호출")
    void scheduled_success() {
        when(batchLog.start("LoginHistoryPurgeJob", "RETENTION")).thenReturn(20L);
        when(policyService.findByTargetTable("login_history")).thenReturn(Optional.of(policy()));
        when(executionMapper.deleteLoginHistory(12)).thenReturn(5);

        job.scheduled();

        verify(batchLog).success(eq(20L), eq(5));
    }
}
