package kr.co.ircp.cms.integration.governance;

import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import kr.co.ircp.cms.domain.governance.repository.BatchExecutionLogMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-009 TS-002: BatchExecutionLogService start/success/failure 라이프사이클 검증.
 */
class BatchExecutionLogServiceIT extends AbstractIntegrationTest {

    @Autowired
    private BatchExecutionLogService service;

    @Autowired
    private BatchExecutionLogMapper mapper;

    @Test
    void startThenSuccess_recordsRunningThenSuccess() {
        Long id = service.start("TestJob", "STATS");
        assertThat(id).isNotNull();

        BatchExecutionLog running = mapper.findById(id).orElseThrow();
        assertThat(running.getStatus()).isEqualTo("RUNNING");
        assertThat(running.getStartedAt()).isNotNull();
        assertThat(running.getJobGroup()).isEqualTo("STATS");

        service.success(id, 42);

        BatchExecutionLog done = mapper.findById(id).orElseThrow();
        assertThat(done.getStatus()).isEqualTo("SUCCESS");
        assertThat(done.getRecordsProcessed()).isEqualTo(42);
        assertThat(done.getFinishedAt()).isNotNull();
        assertThat(done.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void startThenFailure_recordsFailureWithErrorSummary() {
        Long id = service.start("FailingJob", "RETENTION");
        service.failure(id, "Test failure: connection refused");

        BatchExecutionLog log = mapper.findById(id).orElseThrow();
        assertThat(log.getStatus()).isEqualTo("FAILURE");
        assertThat(log.getErrorSummary()).contains("connection refused");
        assertThat(log.getFinishedAt()).isNotNull();
    }

    @Test
    void startThenSkip_recordsSkipped() {
        Long id = service.start("SkippableJob", "STATS");
        service.skip(id, "policy_matching 미존재");

        BatchExecutionLog log = mapper.findById(id).orElseThrow();
        assertThat(log.getStatus()).isEqualTo("SKIPPED");
        assertThat(log.getErrorSummary()).contains("미존재");
    }
}
