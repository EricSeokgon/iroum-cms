package kr.co.ircp.cms.domain.audit.notification;

import kr.co.ircp.cms.domain.audit.service.AuditLogService.AuditLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CriticalAuditNotifier 단위 테스트 — REQ-CROSS-001-D-6.
 */
@DisplayName("CriticalAuditNotifier 단위 테스트")
class CriticalAuditNotifierTest {

    private CriticalAuditNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new CriticalAuditNotifier();
    }

    private AuditLogRecord record(String severity) {
        return new AuditLogRecord(
                Instant.now(),
                1L, "ADMIN",
                "DELETE", "User", "42",
                null, null,
                "10.0.0.1", "test-agent",
                "trace-001",
                severity,
                "SUCCESS", null, 5
        );
    }

    @Test
    @DisplayName("shouldEnqueueOnCriticalSeverity — CRITICAL severity 시 큐에 push된다")
    void shouldEnqueueOnCriticalSeverity() {
        // given
        AuditLogRecord criticalRecord = record("CRITICAL");

        // when
        notifier.enqueue(criticalRecord);

        // then
        assertThat(notifier.queueSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("shouldNotEnqueueOnInfoSeverity — INFO severity 시 큐에 push되지 않는다")
    void shouldNotEnqueueOnInfoSeverity() {
        // given
        AuditLogRecord infoRecord = record("INFO");
        AuditLogRecord warnRecord = record("WARN");

        // when
        notifier.enqueue(infoRecord);
        notifier.enqueue(warnRecord);

        // then
        assertThat(notifier.queueSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("shouldDrainQueueOnRead — drainAll() 호출 시 큐가 비워지고 항목을 반환한다")
    void shouldDrainQueueOnRead() {
        // given
        notifier.enqueue(record("CRITICAL"));
        notifier.enqueue(record("CRITICAL"));
        notifier.enqueue(record("CRITICAL"));
        assertThat(notifier.queueSize()).isEqualTo(3);

        // when
        List<AuditLogRecord> drained = notifier.drainAll();

        // then
        assertThat(drained).hasSize(3);
        assertThat(drained).allMatch(r -> "CRITICAL".equals(r.severity()));
        assertThat(notifier.queueSize())
                .as("drainAll 후 큐가 비어있어야 한다")
                .isEqualTo(0);
    }
}
