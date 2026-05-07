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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonalDataRetentionJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-007 — personal_data_access_log 6개월 보존.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalDataRetentionJob — 개인정보 보존 정책 (REQ-GOV-007)")
class PersonalDataRetentionJobTest {

    @Mock private RetentionPolicyService policyService;
    @Mock private RetentionExecutionMapper executionMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private PersonalDataRetentionJob job;

    private RetentionPolicy policy() {
        return RetentionPolicy.builder()
                .id(1L)
                .targetTable("personal_data_access_log")
                .policyType("ARCHIVE")
                .retentionMonths(6)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run(false) — archive + delete 호출, archive 행 수 반환")
    void run_normal_archivesAndDeletes() {
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.of(policy()));
        when(executionMapper.archivePersonalDataAccessLog(6)).thenReturn(42);
        when(executionMapper.deletePersonalDataAccessLog(6)).thenReturn(42);

        int processed = job.run(false);

        assertThat(processed).isEqualTo(42);
        verify(executionMapper).archivePersonalDataAccessLog(6);
        verify(executionMapper).deletePersonalDataAccessLog(6);
    }

    @Test
    @DisplayName("run(true) — dry-run은 archive/delete 호출 안 함, 0 반환")
    void run_dryRun_skipsArchiveAndDelete() {
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.of(policy()));

        int processed = job.run(true);

        assertThat(processed).isEqualTo(0);
        verify(executionMapper, never()).archivePersonalDataAccessLog(anyInt());
        verify(executionMapper, never()).deletePersonalDataAccessLog(anyInt());
    }

    @Test
    @DisplayName("run — 정책 미존재 시 IllegalStateException")
    void run_policyNotFound_throws() {
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> job.run(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retention_policy not found");
    }

    @Test
    @DisplayName("run — DELETE 실패 시 IllegalStateException 으로 트랜잭션 롤백 (코드 리뷰 #1)")
    void run_deleteFails_throwsToTriggerRollback() {
        // 코드 리뷰 #1: archive 성공 + delete 실패 시 archive 와 source 양쪽 중복 방지를 위해
        // 예외를 전파하여 @Transactional 롤백을 트리거한다. archive 는 ON CONFLICT DO NOTHING 으로
        // 멱등이므로 다음 배치 실행에서 재시도 가능.
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.of(policy()));
        when(executionMapper.archivePersonalDataAccessLog(6)).thenReturn(10);
        when(executionMapper.deletePersonalDataAccessLog(6))
                .thenThrow(new RuntimeException("APPEND-ONLY violation"));

        assertThatThrownBy(() -> job.run(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DELETE 실패")
                .hasMessageContaining("archive 롤백")
                .hasCauseInstanceOf(RuntimeException.class);

        verify(executionMapper).archivePersonalDataAccessLog(6);
        verify(executionMapper).deletePersonalDataAccessLog(6);
    }

    @Test
    @DisplayName("scheduled — DELETE 실패 시 batch_execution_log 에 failure 기록 (코드 리뷰 #1)")
    void scheduled_deleteFails_recordsFailureInBatchLog() {
        // 코드 리뷰 #1 검증: scheduled() 진입 시 GovernanceJobSupport.run 이 RuntimeException 을
        // catch 하고 batchLog.failure 를 호출한다. archive 가 롤백되었음을 운영자가 알 수 있도록
        // error 메시지에 "DELETE 실패" 키워드가 포함된다.
        when(batchLog.start("PersonalDataRetentionJob", "RETENTION")).thenReturn(99L);
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.of(policy()));
        when(executionMapper.archivePersonalDataAccessLog(6)).thenReturn(10);
        when(executionMapper.deletePersonalDataAccessLog(6))
                .thenThrow(new RuntimeException("APPEND-ONLY violation"));

        job.scheduled();

        verify(batchLog).failure(eq(99L),
                org.mockito.ArgumentMatchers.contains("DELETE 실패"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }

    @Test
    @DisplayName("scheduled — batchLog start/success 호출")
    void scheduled_invokesBatchLog() {
        when(batchLog.start("PersonalDataRetentionJob", "RETENTION")).thenReturn(7L);
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.of(policy()));
        when(executionMapper.archivePersonalDataAccessLog(6)).thenReturn(5);

        job.scheduled();

        verify(batchLog).start("PersonalDataRetentionJob", "RETENTION");
        verify(batchLog).success(eq(7L), eq(5));
    }

    @Test
    @DisplayName("scheduled — 정책 미존재 시 GovernanceJobSupport에서 failure 호출")
    void scheduled_policyMissing_recordsFailure() {
        when(batchLog.start("PersonalDataRetentionJob", "RETENTION")).thenReturn(7L);
        when(policyService.findByTargetTable("personal_data_access_log"))
                .thenReturn(Optional.empty());

        job.scheduled();

        verify(batchLog).failure(eq(7L), org.mockito.ArgumentMatchers.contains("retention_policy not found"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }
}
