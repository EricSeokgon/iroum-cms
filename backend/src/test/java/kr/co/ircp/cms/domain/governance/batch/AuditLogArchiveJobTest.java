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
 * AuditLogArchiveJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-008 — audit_log 5년 보존 archive 이관.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogArchiveJob — 감사 로그 archive (REQ-GOV-008)")
class AuditLogArchiveJobTest {

    @Mock private RetentionPolicyService policyService;
    @Mock private RetentionExecutionMapper executionMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private AuditLogArchiveJob job;

    private RetentionPolicy policy() {
        return RetentionPolicy.builder()
                .id(3L)
                .targetTable("audit_log")
                .policyType("ARCHIVE")
                .retentionMonths(60)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — ensureAuditLogArchive 호출 + archive 행 수 반환")
    void run_normal_ensuresAndArchives() {
        when(policyService.findByTargetTable("audit_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.archiveAuditLog(60)).thenReturn(100);

        int processed = job.run();

        assertThat(processed).isEqualTo(100);
        verify(executionMapper).ensureAuditLogArchive();
        verify(executionMapper).archiveAuditLog(60);
    }

    @Test
    @DisplayName("run — 정책 미존재 시 IllegalStateException")
    void run_policyMissing_throws() {
        when(policyService.findByTargetTable("audit_log")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention_policy not found");
    }

    @Test
    @DisplayName("scheduled — 정상 실행 시 success 호출")
    void scheduled_success() {
        when(batchLog.start("AuditLogArchiveJob", "RETENTION")).thenReturn(50L);
        when(policyService.findByTargetTable("audit_log")).thenReturn(Optional.of(policy()));
        when(executionMapper.archiveAuditLog(60)).thenReturn(7);

        job.scheduled();

        verify(batchLog).success(eq(50L), eq(7));
    }
}
