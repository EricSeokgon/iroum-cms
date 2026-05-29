package kr.co.ircp.cms.domain.notification.admin.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.notification.admin.dto.AdminNotificationDto;
import kr.co.ircp.cms.domain.notification.admin.dto.MarkAllReadRequest;
import kr.co.ircp.cms.domain.notification.admin.service.AdminNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 알림 받은편지함 REST 컨트롤러.
 *
 * <p>SPEC-CMS-NOTIFICATION-CENTER-001 REQ-NC-001~005, 010.
 * 모든 엔드포인트는 JWT 의 userId 를 강제 사용하여 권한 격리(REQ-NC-010)를 보장한다.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')")
public class AdminNotificationController {

    private final AdminNotificationService service;

    /**
     * REQ-NC-001 — 알림 목록 조회 (페이지네이션·필터).
     */
    @GetMapping
    public ResponseEntity<PageResponse<AdminNotificationDto>> list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> severity,
            @RequestParam(required = false) List<String> type,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal JwtPrincipal principal) {

        List<String> effectiveStatus = (status == null || status.isEmpty())
                ? List.of("UNREAD", "READ") // 기본: ARCHIVED 제외
                : status;

        // LocalDate → Instant (UTC 기준; to 는 day+1 의 00:00 미만으로 inclusive 처리)
        Instant fromInstant = (from != null) ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = (to != null)
                ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        PageResponse<AdminNotificationDto> result = service.getNotifications(
                principal.userId(),
                effectiveStatus,
                severity,
                type,
                fromInstant,
                toInstant,
                page,
                size);
        return ResponseEntity.ok(result);
    }

    /**
     * REQ-NC-002 — 개별 읽음 처리.
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal JwtPrincipal principal) {
        service.markRead(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * REQ-NC-003 — 일괄 읽음 처리.
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @RequestBody(required = false) MarkAllReadRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        int updated = service.markAllRead(principal.userId(), request);
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }

    /**
     * REQ-NC-004 — 보관 처리.
     */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long id,
                                        @AuthenticationPrincipal JwtPrincipal principal) {
        service.archive(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * REQ-NC-005 — 미읽음 수 (헤더 배지·폴링용).
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal JwtPrincipal principal) {
        long count = service.getUnreadCount(principal.userId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }
}
