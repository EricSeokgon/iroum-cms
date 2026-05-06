package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 배치 실행 이력 엔티티.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010: STATS/RETENTION/QUALITY/RECOVERY
 * 모든 배치의 실행 시작·종료·결과 추적.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecutionLog {

    private Long id;
    private String jobName;
    /** STATS | RETENTION | QUALITY | RECOVERY */
    private String jobGroup;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer durationMs;
    /** RUNNING | SUCCESS | FAILURE | TIMEOUT | RETRYING | SKIPPED */
    private String status;
    private Integer recordsProcessed;
    private Integer recordsFailed;
    private String errorSummary;
    private Integer retryCount;
    /** SCHEDULE | MANUAL */
    private String triggeredBy;
    private Long operatorId;
}
