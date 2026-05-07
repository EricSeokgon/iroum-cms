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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IntegrationLogRetentionJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 — integration_log 6개월 보존, 미존재 시 graceful skip.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationLogRetentionJob — 통합 로그 보존 (graceful skip)")
class IntegrationLogRetentionJobTest {

    @Mock private RetentionPolicyService policyService;
    @Mock private RetentionExecutionMapper executionMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private IntegrationLogRetentionJob job;

    private RetentionPolicy policy() {
        return RetentionPolicy.builder()
                .id(2L)
                .targetTable("integration_log")
                .policyType("ARCHIVE")
                .retentionMonths(6)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — integration_log 미존재 시 0 반환, archive 미호출")
    void run_tableMissing_returnsZero() {
        when(executionMapper.integrationLogExists()).thenReturn(0);

        int processed = job.run();

        assertThat(processed).isEqualTo(0);
        verify(executionMapper, never()).archiveIntegrationLog(anyInt());
        verify(executionMapper, never()).deleteIntegrationLog(anyInt());
    }

    @Test
    @DisplayName("run — 테이블 존재 + 정책 존재 시 archive + delete 호출")
    void run_tableExists_archivesAndDeletes() {
        when(executionMapper.integrationLogExists()).thenReturn(1);
        when(policyService.findByTargetTable("integration_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.archiveIntegrationLog(6)).thenReturn(15);

        int processed = job.run();

        assertThat(processed).isEqualTo(15);
        verify(executionMapper).archiveIntegrationLog(6);
        verify(executionMapper).deleteIntegrationLog(6);
    }

    @Test
    @DisplayName("run — 테이블 존재하지만 정책 없으면 IllegalStateException")
    void run_policyMissing_throws() {
        when(executionMapper.integrationLogExists()).thenReturn(1);
        when(policyService.findByTargetTable("integration_log")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention_policy not found");
    }

    @Test
    @DisplayName("scheduled — 테이블 미존재 시 batchLog.skip 호출")
    void scheduled_tableMissing_invokesSkip() {
        when(executionMapper.integrationLogExists()).thenReturn(0);
        when(batchLog.start("IntegrationLogRetentionJob", "RETENTION")).thenReturn(11L);

        job.scheduled();

        verify(batchLog).start("IntegrationLogRetentionJob", "RETENTION");
        verify(batchLog).skip(eq(11L), contains("integration_log 미존재"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }

    @Test
    @DisplayName("scheduled — 테이블 존재 시 정상 run 통한 success")
    void scheduled_tableExists_runsAndSucceeds() {
        when(executionMapper.integrationLogExists()).thenReturn(1);
        when(batchLog.start("IntegrationLogRetentionJob", "RETENTION")).thenReturn(12L);
        when(policyService.findByTargetTable("integration_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.archiveIntegrationLog(6)).thenReturn(20);

        job.scheduled();

        verify(batchLog).success(eq(12L), eq(20));
    }
}
