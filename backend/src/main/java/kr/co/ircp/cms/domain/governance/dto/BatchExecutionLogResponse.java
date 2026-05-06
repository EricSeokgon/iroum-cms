package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;

import java.time.Instant;

public record BatchExecutionLogResponse(
        Long id,
        String jobName,
        String jobGroup,
        Instant startedAt,
        Instant finishedAt,
        Integer durationMs,
        String status,
        Integer recordsProcessed,
        Integer recordsFailed,
        String errorSummary,
        Integer retryCount,
        String triggeredBy,
        Long operatorId
) {

    public static BatchExecutionLogResponse from(BatchExecutionLog b) {
        return new BatchExecutionLogResponse(
                b.getId(), b.getJobName(), b.getJobGroup(),
                b.getStartedAt(), b.getFinishedAt(), b.getDurationMs(),
                b.getStatus(), b.getRecordsProcessed(), b.getRecordsFailed(),
                b.getErrorSummary(), b.getRetryCount(),
                b.getTriggeredBy(), b.getOperatorId());
    }
}
