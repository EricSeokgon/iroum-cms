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
 * AccessLogRetentionJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 — access_log 3개월 정리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessLogRetentionJob — 접근 로그 정리")
class AccessLogRetentionJobTest {

    @Mock private RetentionPolicyService policyService;
    @Mock private RetentionExecutionMapper executionMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private AccessLogRetentionJob job;

    private RetentionPolicy policy() {
        return RetentionPolicy.builder()
                .id(5L)
                .targetTable("access_log")
                .policyType("DELETE")
                .retentionMonths(3)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — 정책 기반 deleteAccessLog 호출 + 행 수 반환")
    void run_normal_deletes() {
        when(policyService.findByTargetTable("access_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.deleteAccessLog(3)).thenReturn(1000);

        int processed = job.run();

        assertThat(processed).isEqualTo(1000);
        verify(executionMapper).deleteAccessLog(3);
    }

    @Test
    @DisplayName("run — 정책 미존재 시 IllegalStateException")
    void run_policyMissing_throws() {
        when(policyService.findByTargetTable("access_log")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention_policy not found");
    }

    @Test
    @DisplayName("scheduled — 정상 실행 시 batchLog success 호출")
    void scheduled_success() {
        when(batchLog.start("AccessLogRetentionJob", "RETENTION")).thenReturn(30L);
        when(policyService.findByTargetTable("access_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.deleteAccessLog(3)).thenReturn(50);

        job.scheduled();

        verify(batchLog).success(eq(30L), eq(50));
    }
}
