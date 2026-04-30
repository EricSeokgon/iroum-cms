package kr.co.ircp.cms.domain.audit.notification;

import kr.co.ircp.cms.domain.audit.service.AuditLogService.AuditLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * CRITICAL 감사 이벤트 알림 큐 — REQ-CROSS-001-D-6.
 *
 * <p>severity=CRITICAL 행이 적재되었을 때 인앱 알림 큐(메모리
 * {@link ConcurrentLinkedQueue})에 즉시 push한다.
 * {@code GET /api/v1/system/audit-logs/critical} 컨트롤러가 큐를 조회하고 비운다.
 *
 * <p>1차 구현은 인메모리 단일 노드; SMTP 연동 및 멀티노드 동기화는 후속 SPEC.
 */
// @MX:ANCHOR: [AUTO] CriticalAuditNotifier.enqueue — CRITICAL 알림 큐 진입점
// @MX:REASON: AuditLogServiceImpl, CriticalAuditController, 테스트에서 fan_in >= 3
// @MX:WARN: [AUTO] ConcurrentLinkedQueue — 무제한 증가 가능
// @MX:REASON: 큐 드레인은 컨트롤러 호출 시에만 발생. 장기 미조회 시 메모리 압박 위험
@Component
public class CriticalAuditNotifier {

    private static final Logger log = LoggerFactory.getLogger(CriticalAuditNotifier.class);

    private static final String SEVERITY_CRITICAL = "CRITICAL";

    /** 인앱 알림 큐 — thread-safe, 무제한 크기 (1차 구현). */
    private final ConcurrentLinkedQueue<AuditLogRecord> queue = new ConcurrentLinkedQueue<>();

    /**
     * severity=CRITICAL인 경우 알림 큐에 push한다.
     *
     * <p>비-CRITICAL 항목은 무시한다.
     *
     * @param entry 감사 로그 레코드
     */
    public void enqueue(AuditLogRecord entry) {
        if (entry == null) {
            return;
        }
        if (SEVERITY_CRITICAL.equalsIgnoreCase(entry.severity())) {
            queue.add(entry);
            log.warn("CRITICAL 감사 이벤트 알림 큐 push: action={} entity={} actor={}",
                    entry.action(), entry.entityType(), entry.actorId());
        }
    }

    /**
     * 큐의 모든 항목을 반환하고 큐를 비운다 (드레인).
     *
     * <p>{@code GET /api/v1/system/audit-logs/critical} 호출 시 사용.
     *
     * @return 드레인된 CRITICAL 감사 레코드 목록
     */
    public List<AuditLogRecord> drainAll() {
        List<AuditLogRecord> result = new ArrayList<>();
        AuditLogRecord item;
        while ((item = queue.poll()) != null) {
            result.add(item);
        }
        return result;
    }

    /**
     * 현재 큐 크기를 반환한다 (헬스 모니터링용).
     */
    public int queueSize() {
        return queue.size();
    }
}
