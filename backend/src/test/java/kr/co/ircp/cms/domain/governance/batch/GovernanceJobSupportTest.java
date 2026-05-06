package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GovernanceJobSupport.run/skip 헬퍼 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class GovernanceJobSupportTest {

    @Mock
    private BatchExecutionLogService batchLog;

    @Test
    void run_success_callsStartThenSuccess() {
        when(batchLog.start("MyJob", "STATS")).thenReturn(42L);

        GovernanceJobSupport.run(batchLog, "MyJob", "STATS", () -> 100);

        verify(batchLog).start("MyJob", "STATS");
        verify(batchLog).success(eq(42L), eq(100));
        verify(batchLog, never()).failure(anyLong(), any());
    }

    @Test
    void run_exception_callsFailure() {
        when(batchLog.start("FailJob", "RETENTION")).thenReturn(7L);

        GovernanceJobSupport.run(batchLog, "FailJob", "RETENTION", () -> {
            throw new RuntimeException("boom");
        });

        verify(batchLog).start("FailJob", "RETENTION");
        verify(batchLog).failure(eq(7L), contains("boom"));
        verify(batchLog, never()).success(anyLong(), any(Integer.class));
    }

    @Test
    void skip_recordsSkippedStatus() {
        when(batchLog.start("SkipJob", "STATS")).thenReturn(11L);

        GovernanceJobSupport.skip(batchLog, "SkipJob", "STATS", "테이블 미존재");

        verify(batchLog).start("SkipJob", "STATS");
        verify(batchLog).skip(eq(11L), contains("테이블 미존재"));
    }
}
