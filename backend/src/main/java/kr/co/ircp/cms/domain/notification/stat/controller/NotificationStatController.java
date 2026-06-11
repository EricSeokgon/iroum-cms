package kr.co.ircp.cms.domain.notification.stat.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.notification.stat.dto.CategoryStat;
import kr.co.ircp.cms.domain.notification.stat.dto.DailyTrendPoint;
import kr.co.ircp.cms.domain.notification.stat.dto.FailedNotificationDto;
import kr.co.ircp.cms.domain.notification.stat.dto.NotificationStatSummary;
import kr.co.ircp.cms.domain.notification.stat.service.NotificationStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 알림 발송 통계 REST 컨트롤러.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-001~008 — 발송 현황 요약·카테고리·일별 추이·
 * 오류 목록·재발송. 전 엔드포인트 관리자 권한 격리(REQ-NS-007), 재발송은 감사 기록(REQ-NS-005).
 */
// @MX:ANCHOR: [AUTO] NotificationStatController — 통계 API 권한·감사 경계 (REQ-NS-007)
// @MX:REASON: @PreAuthorize 매트릭스 + 재발송 @AuditLog invariant. 비관리자 통계 데이터 노출 금지.
@RestController
@RequestMapping("/api/v1/admin/notifications/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')")
public class NotificationStatController {

    private final NotificationStatService service;

    /** REQ-NS-001 — 발송 현황 요약 (today/7일/30일). */
    @GetMapping("/summary")
    public ResponseEntity<NotificationStatSummary> getSummary() {
        return ResponseEntity.ok(service.getSummary());
    }

    /** REQ-NS-002 — 카테고리(type)별 통계 (기본 최근 30일). */
    @GetMapping("/by-category")
    public ResponseEntity<List<CategoryStat>> getByCategory(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.getByCategory(from, to));
    }

    /** REQ-NS-003/008 — 일별 발송 추이 (기본 최근 30일, 최대 90일 캡). */
    @GetMapping("/daily-trend")
    public ResponseEntity<List<DailyTrendPoint>> getDailyTrend(
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.getDailyTrend(from, to));
    }

    /** REQ-NS-004 — 오류/미발송 목록 (페이지네이션). */
    @GetMapping("/errors")
    public ResponseEntity<PageResponse<FailedNotificationDto>> getErrors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getErrors(page, size));
    }

    /** REQ-NS-005 — 개별 재발송 (delivery_status SENT 정정, 감사 기록). */
    @AuditLog(action = "UPDATE", entityType = "UserNotificationInbox", captureArgs = true)
    @PatchMapping("/errors/{id}/resend")
    public ResponseEntity<Map<String, Object>> resend(@PathVariable Long id) {
        service.resend(id);
        return ResponseEntity.ok(Map.of("id", id, "deliveryStatus", "SENT"));
    }
}
