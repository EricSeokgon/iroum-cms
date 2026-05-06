package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.BatchExecutionLog;
import kr.co.ircp.cms.domain.governance.repository.BatchExecutionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 배치 실행 이력 서비스.
 *
 * <p>SPEC-CMS-009 REQ-DATA-005, REQ-GOV-010 — 모든 거버넌스 배치의
 * start/success/failure/skip 라이프사이클을 batch_execution_log에 기록한다.
 */
// @MX:ANCHOR: [AUTO] BatchExecutionLogService — 14개 거버넌스 배치 Job이 모두 의존 (fan_in >= 14)
// @MX:REASON: 배치 실행 이력 추적의 단일 진입점. start/success/failure 패턴이 SPEC §7.2와 acceptance.md TS-002의 핵심
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-005
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchExecutionLogService {

    private final BatchExecutionLogMapper mapper;

    /**
     * 배치 시작 기록. 새 row의 id를 반환한다.
     * 별도 트랜잭션으로 분리하여 본 배치가 롤백되어도 시작 기록은 유지된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(String jobName, String jobGroup) {
        BatchExecutionLog log = BatchExecutionLog.builder()
                .jobName(jobName)
                .jobGroup(jobGroup)
                .startedAt(Instant.now())
                .status("RUNNING")
                .recordsProcessed(0)
                .recordsFailed(0)
                .retryCount(0)
                .triggeredBy("SCHEDULE")
                .build();
        mapper.insert(log);
        return log.getId();
    }

    /**
     * 배치 성공 기록.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(Long logId, int recordsProcessed) {
        Instant now = Instant.now();
        BatchExecutionLog update = mapper.findById(logId).orElseThrow();
        long durationMs = update.getStartedAt() != null
                ? java.time.Duration.between(update.getStartedAt(), now).toMillis()
                : 0L;
        update.setStatus("SUCCESS");
        update.setFinishedAt(now);
        update.setDurationMs((int) durationMs);
        update.setRecordsProcessed(recordsProcessed);
        update.setErrorSummary(null);
        mapper.update(update);
    }

    /**
     * 배치 실패 기록.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(Long logId, String errorSummary) {
        Instant now = Instant.now();
        BatchExecutionLog update = mapper.findById(logId).orElseThrow();
        long durationMs = update.getStartedAt() != null
                ? java.time.Duration.between(update.getStartedAt(), now).toMillis()
                : 0L;
        update.setStatus("FAILURE");
        update.setFinishedAt(now);
        update.setDurationMs((int) durationMs);
        update.setErrorSummary(truncate(errorSummary));
        mapper.update(update);
    }

    /**
     * 배치 SKIP 기록 (의존 SPEC 미반영 등 graceful degradation).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void skip(Long logId, String reason) {
        Instant now = Instant.now();
        BatchExecutionLog update = mapper.findById(logId).orElseThrow();
        update.setStatus("SKIPPED");
        update.setFinishedAt(now);
        update.setDurationMs(0);
        update.setErrorSummary(truncate(reason));
        mapper.update(update);
    }

    /** 90일 경과 행 삭제. BatchExecutionLogCleanupJob에서 호출. */
    @Transactional
    public int cleanupOlderThan(int days) {
        Instant threshold = Instant.now().minus(java.time.Duration.ofDays(days));
        return mapper.deleteOlderThan(threshold);
    }

    public java.util.Optional<BatchExecutionLog> findById(Long id) {
        return mapper.findById(id);
    }

    public kr.co.ircp.cms.domain.auth.dto.PageResponse<BatchExecutionLog> findFiltered(
            String jobGroup, String status, Instant from, Instant to, int page, int size) {
        java.util.Map<String, Object> p = new java.util.HashMap<>();
        p.put("jobGroup", jobGroup);
        p.put("status", status);
        p.put("from", from);
        p.put("to", to);
        p.put("offset", page * size);
        p.put("size", size);
        java.util.List<BatchExecutionLog> content = mapper.findFiltered(p);
        long total = mapper.countFiltered(p);
        return kr.co.ircp.cms.domain.auth.dto.PageResponse.of(content, page, size, total);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
